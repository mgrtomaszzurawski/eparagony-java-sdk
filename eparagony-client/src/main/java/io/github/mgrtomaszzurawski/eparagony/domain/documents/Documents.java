package io.github.mgrtomaszzurawski.eparagony.domain.documents;

import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.IdempotencyKey;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.IssuedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;

import java.time.Duration;

/**
 * Issuing documents and following what becomes of them. Obtained from
 * {@code EparagonyClient.documents()}.
 *
 * <p>Every method requires the {@code document_create} scope.
 */
public interface Documents {

    /**
     * Issues a fiscal e-receipt. Returns as soon as eparagony.pl accepts the data — the cash register
     * has not fiscalized anything yet.
     *
     * <p>A fresh {@code Idempotency-Key} is generated for this call. If it fails with
     * {@code EparagonyServerException} reporting
     * {@code requestMayHaveBeenApplied() == true}, do <em>not</em> call this again: use
     * {@link #issue(ReceiptRequest, IdempotencyKey)} with the key from the failed attempt, or check
     * the status first.
     */
    IssuedDocument issue(ReceiptRequest request);

    /**
     * Issues a fiscal e-receipt under a caller-supplied idempotency key. Use this to safely repeat a
     * request whose outcome is unknown — the server recognises the key and will not fiscalize the
     * sale twice.
     */
    IssuedDocument issue(ReceiptRequest request, IdempotencyKey idempotencyKey);

    /**
     * Reads a document's current status.
     *
     * <p>Prefer the webhook. eparagony.pl's own guidance is that polling is not the intended path, and
     * a fleet of pollers is what their throttling exists to discourage. Use this to recover a status
     * you missed, or where you have no public endpoint to receive notifications on.
     */
    DocumentStatus status(DocumentToken documentToken);

    /**
     * Polls until the document reaches a terminal state or {@code timeout} elapses, and returns it.
     *
     * <p>Synchronous on purpose. The underlying operation is asynchronous, but handing back a
     * {@code CompletableFuture} would force an execution model on every consumer; those who want one
     * can wrap this call, whereas those who do not cannot unwrap it.
     *
     * <p>Stops on any terminal state — {@code ERROR} and {@code OFFLINE} included, not just success.
     *
     * @throws io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException if the
     *     timeout elapses first. The document is not lost; it simply had not settled yet.
     */
    DocumentStatus awaitTerminalStatus(DocumentToken documentToken, Duration timeout);
}
