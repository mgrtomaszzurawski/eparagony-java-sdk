package io.github.mgrtomaszzurawski.eparagony.core.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A bearer token together with what it is actually good for. The SDK caches one of these and reuses
 * it until it nears expiry — the API documentation warns that minting a token per request triggers
 * throttling.
 *
 * @param value the opaque bearer value
 * @param tokenType the OAuth token type, {@code Bearer} in practice
 * @param grantedScopes scopes the server confirmed, parsed from its {@code scope} response field
 * @param expiresAt the instant the server's {@code expires_in} elapses
 */
public record AccessToken(String value, String tokenType, Set<Scope> grantedScopes, Instant expiresAt) {

    /**
     * How long before nominal expiry a token is treated as expired. Covers clock skew against the
     * authorization server and the flight time of a request that starts just under the wire.
     */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    public AccessToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(tokenType, "tokenType");
        Objects.requireNonNull(expiresAt, "expiresAt");
        grantedScopes = Set.copyOf(Objects.requireNonNull(grantedScopes, "grantedScopes"));
    }

    /** {@code true} once the token is within {@link #EXPIRY_MARGIN} of expiring, or already past it. */
    public boolean isExpiredAt(Instant now) {
        return !now.plus(EXPIRY_MARGIN).isBefore(expiresAt);
    }

    /** {@code true} when the server confirmed every one of the given scopes. */
    public boolean covers(Set<Scope> required) {
        return grantedScopes.containsAll(required);
    }

    /** The {@code Authorization} header value, e.g. {@code Bearer eyJ...}. */
    public String authorizationHeaderValue() {
        return tokenType + " " + value;
    }

    @Override
    public String toString() {
        return "AccessToken[tokenType=" + tokenType + ", grantedScopes=" + grantedScopes
                + ", expiresAt=" + expiresAt + ", value=<redacted>]";
    }
}
