package io.github.mgrtomaszzurawski.eparagony.core.webhook;

import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;

/**
 * A webhook notification did not carry a signature matching its body.
 *
 * <p>Remediation is unlike every other failure in this SDK, which is why this is its own type: do not
 * retry, do not repair, do not process the payload. Answer the caller with a client error and treat
 * the notification as untrusted. Either it was forged, or the body was altered in transit, or — the
 * overwhelmingly common cause in practice — the verifier was handed a re-serialized JSON string
 * rather than the raw bytes the server signed.
 */
public final class WebhookSignatureException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public WebhookSignatureException(String message) {
        super(message);
    }
}
