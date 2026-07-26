package io.github.mgrtomaszzurawski.eparagony.core.error;

/**
 * The {@code Idempotency-Key} sent with a document-issuing request was missing, or was reused for a
 * payload that differs from the one it was first seen with ({@code HTTP 422}).
 *
 * <p>Remediation: reuse a key only when resending a byte-identical request. Retrying a <em>changed</em>
 * document under the original key is the mistake this status exists to catch — issue a fresh key
 * instead. The SDK generates one per call unless the caller supplies their own.
 */
public final class EparagonyIdempotencyException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public EparagonyIdempotencyException(String message) {
        super(message);
    }
}
