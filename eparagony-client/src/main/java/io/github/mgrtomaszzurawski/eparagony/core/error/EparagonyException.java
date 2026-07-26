package io.github.mgrtomaszzurawski.eparagony.core.error;

/**
 * Base type for every failure the SDK raises. Subtypes are grouped by <em>what the caller can do
 * about it</em>, not by HTTP status: catching a subtype tells you the remediation, catching this
 * type tells you only that the call did not succeed.
 *
 * <p>Unchecked by design — a fiscal document is issued from application code that already has an
 * error path, and checked exceptions on every call would be noise.
 */
public class EparagonyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EparagonyException(String message) {
        super(message);
    }

    public EparagonyException(String message, Throwable cause) {
        super(message, cause);
    }
}
