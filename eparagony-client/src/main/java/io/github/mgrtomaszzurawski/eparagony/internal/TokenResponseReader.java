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
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;

import java.time.Clock;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Reads what the authorization server sent back, and refuses anything that would fail later in a way
 * nobody could diagnose. Internal: never exported.
 *
 * <p>Split out of {@link TokenManager}, which was doing three jobs at once — caching, transport and
 * interpretation. This is the interpretation, and it is the part with the judgement in it.
 */
final class TokenResponseReader {

    private static final String FIELD_ACCESS_TOKEN = "access_token";
    private static final String FIELD_TOKEN_TYPE = "token_type";
    private static final String FIELD_EXPIRES_IN = "expires_in";
    private static final String FIELD_SCOPE = "scope";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_ERROR_DESCRIPTION = "error_description";

    private static final String DEFAULT_TOKEN_TYPE = "Bearer";
    private static final int HTTP_SERVER_ERROR_MIN = 500;
    /** A year. The sandbox issues 3600; anything past this is a malformed response, not a long lease. */
    private static final long MAX_TOKEN_LIFETIME_SECONDS = 365L * 24 * 60 * 60;

    private final JsonCodec codec;
    private final Clock clock;

    TokenResponseReader(JsonCodec codec, Clock clock) {
        this.codec = codec;
        this.clock = clock;
    }

    /**
     * Turns a successful response into a token, or refuses it.
     *
     * <p>The refusal that matters: the server answers {@code HTTP 200} for a scope string it does not
     * recognise, omitting {@code scope} and issuing a token every endpoint then rejects with a bare
     * {@code 403 Access denied}. A token that cannot work is not a token, so it is rejected here,
     * naming the scopes that were not granted.
     */
    AccessToken read(String body, Set<Scope> requestedScopes, String requestedScopeValue) {
        JsonNode root = codec.readTree(body);
        JsonNode value = root.get(FIELD_ACCESS_TOKEN);
        if (value == null || !value.isTextual()) {
            throw new EparagonyAuthException(
                    "Authorization server returned HTTP 200 without an access_token");
        }
        JsonNode grantedScopeNode = root.get(FIELD_SCOPE);
        String grantedScopeValue = grantedScopeNode == null ? null : grantedScopeNode.asText(null);
        Set<Scope> granted = Scope.parseWireValue(grantedScopeValue);
        requireScopesGranted(granted, requestedScopes, requestedScopeValue, grantedScopeValue);

        long lifetimeSeconds = readLifetime(root.get(FIELD_EXPIRES_IN));
        JsonNode tokenType = root.get(FIELD_TOKEN_TYPE);
        return new AccessToken(
                value.asText(),
                tokenType != null && tokenType.isTextual() ? tokenType.asText() : DEFAULT_TOKEN_TYPE,
                granted,
                clock.instant().plusSeconds(lifetimeSeconds));
    }

    /**
     * Reads {@code expires_in}, refusing a lifetime that cannot be used.
     *
     * <p>{@code isNumber()} alone was not enough, in both directions. A huge value overflows
     * {@code Instant.plusSeconds} and surfaces as an {@code ArithmeticException} from outside the
     * {@code EparagonyException} hierarchy. A zero or negative one is worse because it works: the
     * token is born expired, so the cache mints a fresh one for every single call — which is the
     * documented way to earn a {@code 429} from this API, arriving as a mystery.
     */
    private static long readLifetime(JsonNode expiresIn) {
        if (expiresIn == null || !expiresIn.isNumber() || !expiresIn.canConvertToLong()) {
            throw new EparagonyAuthException(
                    "Authorization server returned a token without a usable expires_in");
        }
        long seconds = expiresIn.asLong();
        if (seconds <= 0 || seconds > MAX_TOKEN_LIFETIME_SECONDS) {
            throw new EparagonyAuthException(
                    "Authorization server returned a token whose expires_in is " + seconds
                            + " seconds, which is not a usable lifetime. A non-positive value would "
                            + "make the SDK re-mint a token on every call, and the API rate-limits "
                            + "exactly that.");
        }
        return seconds;
    }

    /** Turns a failed response into the exception whose remediation matches. */
    EparagonyException readFailure(int status, String body, String requestedScopeValue) {
        String detail = describeFailure(body);
        if (status >= HTTP_SERVER_ERROR_MIN) {
            return new EparagonyServerException(
                    "Authorization server returned HTTP " + status + detail, status, false);
        }
        return new EparagonyAuthException("Token request rejected with HTTP " + status
                + " for scope \"" + requestedScopeValue + "\"" + detail);
    }

    private static void requireScopesGranted(Set<Scope> granted, Set<Scope> requested,
            String requestedScopeValue, String grantedScopeValue) {
        if (granted.containsAll(requested)) {
            return;
        }
        StringJoiner missing = new StringJoiner(", ");
        requested.stream()
                .filter(scope -> !granted.contains(scope))
                .forEach(scope -> missing.add(scope.wireValue()));
        throw new EparagonyAuthException(
                "Authorization server issued a token that does not grant [" + missing + "]. "
                        + "Requested scope was \"" + requestedScopeValue + "\"; the response "
                        + describeGranted(grantedScopeValue)
                        + ". A token without the required scope is accepted by the authorization server "
                        + "but rejected by every endpoint with an opaque 403, so it is refused here. "
                        + "Request the missing scope from eparagony.pl support, or narrow "
                        + "EparagonyConfig.scopes() to what this client is granted.");
    }

    private static String describeGranted(String grantedScopeValue) {
        // Two whole clauses, not a shared prefix: the absent case is the documented live behaviour —
        // HTTP 200 with no `scope` field at all — so this is the sentence a reader most often sees.
        return grantedScopeValue == null
                ? "carried no scope field at all"
                : "granted " + ServerText.quoted(grantedScopeValue, "");
    }

    /**
     * Renders a failed token response, extracting only the fields OAuth 2 defines for the purpose.
     *
     * <p>Never the raw body. This is the one request in the SDK whose payload carries the
     * {@code client_secret}, and an authorization server that echoes the request back on a validation
     * error would put that secret into whatever log the consumer writes the exception to.
     */
    private String describeFailure(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        JsonNode root;
        try {
            root = codec.readTree(body);
        } catch (EparagonyException notJson) {
            // A gateway can answer with HTML, and that is exactly the case where echoing the body
            // would be least safe.
            return "";
        }
        StringJoiner detail = new StringJoiner(": ");
        for (String field : new String[] {FIELD_ERROR, FIELD_ERROR_DESCRIPTION}) {
            JsonNode candidate = root.get(field);
            if (candidate != null && candidate.isTextual() && !candidate.asText().isBlank()) {
                detail.add(ServerText.safe(candidate.asText()));
            }
        }
        return detail.length() == 0 ? "" : ": " + detail;
    }

}
