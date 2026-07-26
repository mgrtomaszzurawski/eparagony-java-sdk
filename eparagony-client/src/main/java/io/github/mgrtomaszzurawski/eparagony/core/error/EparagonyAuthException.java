package io.github.mgrtomaszzurawski.eparagony.core.error;

/**
 * Authentication failed: the credentials were rejected, a requested scope is not granted to the
 * client, or the authorization server issued a token the SDK refuses to use.
 *
 * <p>Remediation: fix {@code clientId}/{@code clientSecret}, or have the missing scope granted by
 * eparagony.pl support. Retrying with the same inputs cannot help.
 *
 * <p>The last case deserves explanation, because the server makes it easy to get wrong. The token
 * endpoint answers {@code HTTP 200} even for a scope string it does not recognise, omitting the
 * {@code scope} field from the response and issuing a token that is rejected with an opaque
 * {@code 403 Access denied} by every endpoint. The SDK therefore treats a token response without a
 * granted {@code scope} as an authentication failure here, at the point of cause, rather than
 * letting the caller discover it later as an unexplainable 403.
 */
public final class EparagonyAuthException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public EparagonyAuthException(String message) {
        super(message);
    }

    public EparagonyAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
