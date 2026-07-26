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

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mgrtomaszzurawski.eparagony.core.auth.AccessToken;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Credentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;
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
import java.util.Set;
import java.util.StringJoiner;

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

    private static final String FIELD_ACCESS_TOKEN = "access_token";
    private static final String FIELD_TOKEN_TYPE = "token_type";
    private static final String FIELD_EXPIRES_IN = "expires_in";
    private static final String FIELD_SCOPE = "scope";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_API_VERSION = "X-Api-Version";

    private static final String MEDIA_TYPE_FORM = "application/x-www-form-urlencoded";
    private static final String MEDIA_TYPE_JSON = "application/json";

    private static final String DEFAULT_TOKEN_TYPE = "Bearer";
    private static final int HTTP_OK = 200;
    private static final int HTTP_SERVER_ERROR_MIN = 500;

    private final HttpClient httpClient;
    private final EparagonyConfig config;
    private final String userAgent;
    private final JsonCodec codec;
    private final Clock clock;
    private final Object acquisitionLock = new Object();

    private volatile AccessToken cachedToken;

    public TokenManager(HttpClient httpClient, EparagonyConfig config, String userAgent, JsonCodec codec,
            Clock clock) {
        this.httpClient = httpClient;
        this.config = config;
        this.userAgent = userAgent;
        this.codec = codec;
        this.clock = clock;
    }

    /**
     * The current token, minting one if there is none or the cached one is about to expire.
     *
     * <p>Returns the token <em>by value</em>. Re-reading the field after acquisition would race with a
     * concurrent {@link #invalidate()} and hand the caller a null bearer.
     */
    public AccessToken currentToken() {
        AccessToken existing = cachedToken;
        Instant currentInstant = clock.instant();
        if (existing != null && !existing.isExpiredAt(currentInstant)) {
            return existing;
        }
        synchronized (acquisitionLock) {
            // Re-check inside the lock: another thread may have minted one while this one waited.
            AccessToken current = cachedToken;
            if (current != null && !current.isExpiredAt(clock.instant())) {
                return current;
            }
            AccessToken fresh = requestToken();
            cachedToken = fresh;
            return fresh;
        }
    }

    /** Drops the cached token so the next call mints a fresh one. Used after a 401. */
    public void invalidate() {
        synchronized (acquisitionLock) {
            cachedToken = null;
        }
    }

    private AccessToken requestToken() {
        String requestedScopes = Scope.toWireValue(config.scopes());
        HttpResponse<String> response = send(buildRequest(requestedScopes));
        int status = response.statusCode();
        if (status != HTTP_OK) {
            throw tokenFailure(status, response.body(), requestedScopes);
        }
        return parseToken(response.body(), requestedScopes);
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

    private AccessToken parseToken(String body, String requestedScopes) {
        JsonNode root = codec.readTree(body);
        JsonNode value = root.get(FIELD_ACCESS_TOKEN);
        if (value == null || !value.isTextual()) {
            throw new EparagonyAuthException(
                    "Authorization server returned HTTP 200 without an access_token");
        }
        JsonNode grantedScopeNode = root.get(FIELD_SCOPE);
        String grantedScopeValue = grantedScopeNode == null ? null : grantedScopeNode.asText(null);
        Set<Scope> granted = Scope.parseWireValue(grantedScopeValue);
        requireScopesGranted(granted, requestedScopes, grantedScopeValue);

        JsonNode tokenType = root.get(FIELD_TOKEN_TYPE);
        JsonNode expiresIn = root.get(FIELD_EXPIRES_IN);
        if (expiresIn == null || !expiresIn.isNumber()) {
            throw new EparagonyAuthException(
                    "Authorization server returned a token without a usable expires_in");
        }
        return new AccessToken(
                value.asText(),
                tokenType != null && tokenType.isTextual() ? tokenType.asText() : DEFAULT_TOKEN_TYPE,
                granted,
                clock.instant().plusSeconds(expiresIn.asLong()));
    }

    private void requireScopesGranted(Set<Scope> granted, String requestedScopes, String grantedScopeValue) {
        if (granted.containsAll(config.scopes())) {
            return;
        }
        StringJoiner missing = new StringJoiner(", ");
        config.scopes().stream()
                .filter(scope -> !granted.contains(scope))
                .forEach(scope -> missing.add(scope.wireValue()));
        throw new EparagonyAuthException(
                "Authorization server issued a token that does not grant [" + missing + "]. "
                        + "Requested scope was \"" + requestedScopes + "\"; the response "
                        + describeGranted(grantedScopeValue)
                        + ". A token without the required scope is accepted by the authorization server "
                        + "but rejected by every endpoint with an opaque 403, so it is refused here. "
                        + "Request the missing scope from eparagony.pl support, or narrow "
                        + "EparagonyConfig.scopes() to what this client is granted.");
    }

    private static String describeGranted(String grantedScopeValue) {
        return grantedScopeValue == null
                ? "carried no scope field at all"
                : "granted \"" + grantedScopeValue + "\"";
    }

    private EparagonyException tokenFailure(int status, String body, String requestedScopes) {
        String detail = body == null || body.isBlank() ? "" : ": " + body;
        if (status >= HTTP_SERVER_ERROR_MIN) {
            return new EparagonyServerException(
                    "Authorization server returned HTTP " + status + detail, status, false);
        }
        return new EparagonyAuthException("Token request rejected with HTTP " + status
                + " for scope \"" + requestedScopes + "\"" + detail);
    }

    private static String urlEncode(Map<String, String> form) {
        StringJoiner encoded = new StringJoiner("&");
        form.forEach((key, value) -> encoded.add(
                URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return encoded.toString();
    }
}
