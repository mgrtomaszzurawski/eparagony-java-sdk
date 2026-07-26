/*
 * eparagony-java-sdk — a typed Java client for the eparagony.pl Documents REST API.
 * Copyright (C) 2026 Tomasz Zurawski
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.mgrtomaszzurawski.eparagony.internal;

import io.github.mgrtomaszzurawski.eparagony.core.auth.AccessToken;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Credentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Acquires and caches the access token. Internal: never exported.
 *
 * <p>Authentication is <strong>lazy</strong> — no token is minted until the first API call — and the
 * token is then reused until it nears expiry. That is not an optimization: the API's integration
 * guide states plainly that minting a token per request causes throttling.
 *
 * <p>Acquisition is <strong>single-flight</strong>. Without it, a burst of concurrent first calls
 * would each mint their own token, which is exactly the pattern the server throttles.
 *
 * <p>Acquisition <strong>validates the grant</strong>, and this is the part worth reading. The
 * authorization server answers {@code HTTP 200} even for a scope string it does not recognise. It
 * simply omits {@code scope} from the response and hands back a token that every endpoint rejects
 * with a bare {@code 403 Access denied} — no mention of scopes anywhere in the failure. The published
 * specification helps this along by documenting the separator as a comma when the server wants a
 * space, so the natural reading of the docs produces exactly the broken case. This class therefore
 * refuses a token whose granted scopes do not cover what was asked for, and reports it as an
 * authentication failure naming the missing scopes.
 */
public final class TokenManager {

    private static final String PARAM_GRANT_TYPE = "grant_type";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CLIENT_SECRET = "client_secret";
    private static final String PARAM_SCOPE = "scope";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_API_VERSION = "X-Api-Version";

    private static final String MEDIA_TYPE_FORM = "application/x-www-form-urlencoded";
    private static final String MEDIA_TYPE_JSON = "application/json";

    private static final int HTTP_OK = 200;

    private final HttpClient httpClient;
    private final EparagonyConfig config;
    private final String userAgent;
    private final Clock clock;
    private final TokenResponseReader reader;
    private final Object acquisitionLock = new Object();

    /**
     * The cached token.
     *
     * <p>An {@link AtomicReference}, not a {@code volatile} field. {@code volatile} would publish the
     * reference safely enough — {@link AccessToken} is immutable — but it cannot express the operation
     * {@link #invalidate(AccessToken)} actually needs, which is "clear this only if it is still the
     * token that was rejected". Written by hand under the lock that was a lock doing an atomic's job.
     */
    private final AtomicReference<AccessToken> cachedToken = new AtomicReference<>();

    public TokenManager(HttpClient httpClient, EparagonyConfig config, String userAgent, JsonCodec codec,
            Clock clock) {
        this.httpClient = httpClient;
        this.config = config;
        this.userAgent = userAgent;
        this.clock = clock;
        this.reader = new TokenResponseReader(codec, clock);
    }

    /**
     * The current token, minting one if there is none or the cached one is about to expire.
     *
     * <p>Returns the token <em>by value</em>. Re-reading the field after acquisition would race with a
     * concurrent {@link #invalidate(AccessToken)} and hand the caller a null bearer.
     */
    public AccessToken currentToken() {
        AccessToken existing = cachedToken.get();
        Instant currentInstant = clock.instant();
        if (existing != null && !existing.isExpiredAt(currentInstant)) {
            return existing;
        }
        synchronized (acquisitionLock) {
            // Re-check inside the lock: another thread may have minted one while this one waited.
            AccessToken current = cachedToken.get();
            if (current != null && !current.isExpiredAt(clock.instant())) {
                return current;
            }
            AccessToken fresh = requestToken();
            cachedToken.set(fresh);
            return fresh;
        }
    }

    /**
     * Drops the cached token so the next call mints a fresh one, but only if the cache still holds the
     * token the caller found wanting.
     *
     * <p>Compare-and-clear rather than an unconditional clear. Under a burst of concurrent 401s — one
     * expired token in flight on many threads — an unconditional clear has each thread discard the
     * token some other thread has just minted, and they serialize into N token requests. That is
     * precisely the pattern the authorization server throttles, arrived at by the code written to
     * avoid it.
     *
     * @param staleToken the token that was rejected; ignored if the cache has already moved on
     */
    public void invalidate(AccessToken staleToken) {
        cachedToken.compareAndSet(staleToken, null);
    }

    private AccessToken requestToken() {
        String requestedScopes = Scope.toWireValue(config.scopes());
        HttpResponse<String> response = send(buildRequest(requestedScopes));
        int status = response.statusCode();
        if (status != HTTP_OK) {
            throw reader.readFailure(status, response.body(), requestedScopes);
        }
        return reader.read(response.body(), config.scopes(), requestedScopes);
    }

    private HttpRequest buildRequest(String requestedScopes) {
        Credentials credentials = config.credentials();
        if (!(credentials instanceof ClientCredentials clientCredentials)) {
            throw new EparagonyAuthException(
                    "Unsupported credentials type " + credentials.getClass().getSimpleName());
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put(PARAM_GRANT_TYPE, credentials.grantType());
        form.put(PARAM_CLIENT_ID, clientCredentials.clientId());
        form.put(PARAM_CLIENT_SECRET, clientCredentials.clientSecret());
        form.put(PARAM_SCOPE, requestedScopes);

        return HttpRequest.newBuilder()
                .uri(URI.create(config.authBaseUrl() + ApiPaths.AUTH_TOKEN))
                .timeout(config.requestTimeout())
                .header(HEADER_CONTENT_TYPE, MEDIA_TYPE_FORM)
                .header(HEADER_ACCEPT, MEDIA_TYPE_JSON)
                .header(HEADER_USER_AGENT, userAgent)
                .header(HEADER_API_VERSION, ApiVersion.CURRENT)
                .POST(HttpRequest.BodyPublishers.ofString(urlEncode(form), StandardCharsets.UTF_8))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new EparagonyServerException("Token request to " + config.authBaseUrl()
                    + ApiPaths.AUTH_TOKEN + " failed", failure, false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EparagonyServerException("Token request was interrupted", interrupted, false);
        }
    }

    /**
     * Renders a failed token response for an exception message, extracting only the fields OAuth 2
     * defines for the purpose.
     *
     * <p>Never the raw body. This is the one request in the SDK whose payload carries the
     * {@code client_secret}, and an authorization server that echoes the request back — some do, on
     * a validation error — would put that secret into whatever log the consumer writes the exception
     * to. Extracting named fields, and capping their length, keeps the diagnosis without the
     * disclosure.
     */

    private static String urlEncode(Map<String, String> form) {
        StringJoiner encoded = new StringJoiner("&");
        form.forEach((key, value) -> encoded.add(
                URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return encoded.toString();
    }
}
