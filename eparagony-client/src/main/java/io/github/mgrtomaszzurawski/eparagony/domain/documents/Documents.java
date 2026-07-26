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
package io.github.mgrtomaszzurawski.eparagony.domain.documents;

import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.IdempotencyKey;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentAction;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.IssuedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.SignedDocument;

import java.time.Duration;
import java.util.List;

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

    /**
     * Reads the status of every asynchronous action attached to a document.
     *
     * <p>Actions are processes run <em>for</em> a document rather than the document itself — delivering
     * a receipt to Allegro, for instance. Each gets its own webhook; this is the pull equivalent.
     *
     * <p>Requires the {@code document_action_get} scope, which eparagony.pl grants on request.
     */
    List<DocumentAction> actions(DocumentToken documentToken);

    /**
     * Fetches the signed JWS form of an issued document — the cryptographic original, as opposed to
     * the visualization a customer sees.
     *
     * <p>Not needed to issue documents; the API's own guidance says so. Fetch it when you need to
     * archive or independently verify what was issued.
     *
     * <p>Requires the {@code document_get_jws} scope, which eparagony.pl grants only after agreeing
     * the purpose and access rules with you.
     */
    SignedDocument signedDocument(DocumentToken documentToken);
}
