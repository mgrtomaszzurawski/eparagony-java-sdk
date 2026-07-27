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
package io.github.mgrtomaszzurawski.eparagony.internal.client.documents;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.IdempotencyKey;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ActionState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ActionType;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentAction;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.IssuedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.SignedDocument;
import io.github.mgrtomaszzurawski.eparagony.internal.ApiPaths;
import io.github.mgrtomaszzurawski.eparagony.internal.HttpRuntime;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonCodec;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonReader;
import io.github.mgrtomaszzurawski.eparagony.internal.PathTemplate;
import io.github.mgrtomaszzurawski.eparagony.internal.ServerText;
import io.github.mgrtomaszzurawski.eparagony.internal.ScopeGuard;
import io.github.mgrtomaszzurawski.eparagony.internal.RawResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Wires {@link Documents} onto the transport. Internal: never exported. */
public final class DocumentsImpl implements Documents {

    private static final String PATH_PARAM_DOCUMENT_TOKEN = "documentToken";

    private static final String FIELD_TRANSACTION_TOKEN = "transactionToken";
    /** Doubles as the response field name; the wire uses one word for both. */
    private static final String FIELD_DOCUMENT_TOKEN = PATH_PARAM_DOCUMENT_TOKEN;
    private static final String FIELD_DOCUMENT_PUBLIC_URL = "documentPublicUrl";
    private static final String FIELD_DOCUMENT_STATUS_URL = "documentStatusUrl";
    private static final String FIELD_ACTIONS = "actions";
    private static final String FIELD_ACTION_ID = "actionId";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_JWS = "JWS";

    /** {@code 202} means the data was accepted and the register is still working. */
    private static final int HTTP_ACCEPTED = 202;
    /**
     * What the last poll actually answered. Reported instead of the "no response" sentinel because a
     * response is exactly what every poll got; only the document had not settled.
     */
    private static final int HTTP_LAST_POLL_OK = 200;

    /** How long to wait between status polls. Fiscalization takes seconds, not milliseconds. */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final HttpRuntime httpRuntime;
    private final JsonCodec codec;
    private final PosId posId;
    private final Clock clock;
    private final ScopeGuard scopeGuard;

    public DocumentsImpl(HttpRuntime httpRuntime, JsonCodec codec, PosId posId, Clock clock,
            ScopeGuard scopeGuard) {
        this.httpRuntime = httpRuntime;
        this.codec = codec;
        this.posId = posId;
        this.clock = clock;
        this.scopeGuard = scopeGuard;
    }

    @Override
    public IssuedDocument issue(ReceiptRequest request) {
        return issue(request, IdempotencyKey.random());
    }

    @Override
    public IssuedDocument issue(ReceiptRequest request, IdempotencyKey idempotencyKey) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        scopeGuard.require(Scope.DOCUMENT_CREATE, "documents().issue()");
        RawResponse response = httpRuntime.post(
                ApiPaths.DOCUMENTS, ReceiptRequestMapper.toPayload(request, posId), idempotencyKey);
        return toIssuedDocument(response);
    }

    @Override
    public DocumentStatus status(DocumentToken documentToken) {
        Objects.requireNonNull(documentToken, PATH_PARAM_DOCUMENT_TOKEN);
        scopeGuard.require(Scope.DOCUMENT_CREATE, "documents().status()");
        String path = PathTemplate.expand(
                ApiPaths.DOCUMENT_STATUS, PATH_PARAM_DOCUMENT_TOKEN, documentToken.value());
        return DocumentStatusMapper.fromJson(codec.readTree(httpRuntime.getRaw(path)));
    }

    @Override
    public DocumentStatus awaitTerminalStatus(DocumentToken documentToken, Duration timeout) {
        Objects.requireNonNull(documentToken, PATH_PARAM_DOCUMENT_TOKEN);
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive but was " + timeout);
        }
        Instant deadline = clock.instant().plus(timeout);
        // Bounded by a poll count as well as by the deadline. The clock is injectable, and a test
        // clock that does not advance would otherwise make this loop forever — a hang is a worse
        // failure than a timeout, and harder to diagnose.
        long maxPolls = Math.max(1, timeout.toMillis() / POLL_INTERVAL.toMillis() + 1);
        for (long poll = 0; poll < maxPolls; poll++) {
            DocumentStatus current = status(documentToken);
            if (current.isTerminal()) {
                return current;
            }
            if (!clock.instant().plus(POLL_INTERVAL).isBefore(deadline)) {
                throw notSettled(documentToken, current, timeout);
            }
            sleep();
        }
        throw notSettled(documentToken, status(documentToken), timeout);
    }

    /**
     * Giving up waiting is not a failure of the document.
     *
     * <p>{@code requestMayHaveBeenApplied} is {@code false} because it describes <em>this</em>
     * request, and this request was a read: the polls changed nothing. It emphatically does not mean
     * the document is unfiscalized — it already exists, and the register is still working on it. The
     * message says so outright, because a caller who reads the flag as "not fiscalized" and reissues
     * would charge the customer twice.
     *
     * <p>{@code HTTP_LAST_POLL_OK}, not {@code NO_HTTP_RESPONSE}: every poll answered {@code 200}.
     * Claiming no response was ever produced would be false, and the sentinel exists for genuine
     * transport failures.
     */
    private static EparagonyServerException notSettled(DocumentToken documentToken,
            DocumentStatus current, Duration timeout) {
        return new EparagonyServerException(
                "Document " + documentToken + " was still " + current.state() + " after " + timeout
                        + "; it has not failed, it has not settled yet. The document exists and is"
                        + " being fiscalized — keep polling or wait for the webhook. Do not reissue"
                        + " it; that would fiscalize the sale twice.",
                HTTP_LAST_POLL_OK, false);
    }

    @Override
    public List<DocumentAction> actions(DocumentToken documentToken) {
        Objects.requireNonNull(documentToken, PATH_PARAM_DOCUMENT_TOKEN);
        scopeGuard.require(Scope.DOCUMENT_ACTION_GET, "documents().actions()");
        String path = PathTemplate.expand(
                ApiPaths.DOCUMENT_ACTIONS_STATUS, PATH_PARAM_DOCUMENT_TOKEN, documentToken.value());
        JsonNode root = codec.readTree(httpRuntime.getRaw(path));
        JsonNode actions = root.get(FIELD_ACTIONS);
        if (actions == null || !actions.isArray()) {
            // The spec marks `actions` required. A document with none should therefore arrive as an
            // empty array, not an absent field — but an absent field is not worth an exception here,
            // since "no actions" is the honest reading either way.
            return List.of();
        }
        List<DocumentAction> parsed = new ArrayList<>();
        for (JsonNode action : actions) {
            parsed.add(new DocumentAction(
                    JsonReader.text(action, FIELD_ACTION_ID),
                    ActionType.fromWireValue(JsonReader.text(action, FIELD_TYPE)),
                    ActionState.fromWireValue(JsonReader.text(action, FIELD_STATUS))));
        }
        return List.copyOf(parsed);
    }

    @Override
    public SignedDocument signedDocument(DocumentToken documentToken) {
        Objects.requireNonNull(documentToken, PATH_PARAM_DOCUMENT_TOKEN);
        scopeGuard.require(Scope.DOCUMENT_GET_JWS, "documents().signedDocument()");
        String path = PathTemplate.expand(
                ApiPaths.DOCUMENT_JWS, PATH_PARAM_DOCUMENT_TOKEN, documentToken.value());
        JsonNode root = codec.readTree(httpRuntime.getRaw(path));
        return new SignedDocument(required(root, FIELD_JWS));
    }


    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EparagonyServerException("Interrupted while awaiting a document status",
                    interrupted, false);
        }
    }

    private IssuedDocument toIssuedDocument(RawResponse response) {
        // Everything from parsing onward, deliberately. This runs AFTER the sale has been fiscalized,
        // so any failure here — an unparseable body, a required field the server dropped, a token
        // whose shape we cannot model — leaves the caller holding an exception for a document that
        // exists. The documented remediation for a failed issue() is to reissue, which would
        // fiscalize the same sale twice, and only the "may have been applied" flag stops that.
        // Leaving readTree outside this block was exactly that hazard, one line up.
        try {
            JsonNode root = codec.readTree(response.body());
            return new IssuedDocument(
                    TransactionToken.of(required(root, FIELD_TRANSACTION_TOKEN)),
                    DocumentToken.of(required(root, FIELD_DOCUMENT_TOKEN)),
                    required(root, FIELD_DOCUMENT_PUBLIC_URL),
                    required(root, FIELD_DOCUMENT_STATUS_URL),
                    response.statusCode() == HTTP_ACCEPTED);
        } catch (IllegalArgumentException | EparagonyException unmodellable) {
            // Both arms, not just the malformed one: a REQUIRED field the server stopped sending is
            // the same situation as one it garbled — the sale is fiscalized and the caller is holding
            // an exception. Only the "may have been applied" flag keeps them from reissuing it.
            throw new EparagonyServerException(
                    "The document was accepted but the server's response could not be modelled: "
                            + describeCause(unmodellable)
                            + ". Do not reissue — that would fiscalize the sale twice.",
                    unmodellable, true);
        }
    }

    /**
     * Reads a field the specification marks required. No defensive fallback: if the server stops
     * sending one of these, that is a contract violation and it should be loud rather than silently
     * producing a document record with a hole in it.
     */
    /** The cause's own message, bounded. Not re-quoted: it already quotes the offending value. */
    private static String describeCause(Exception cause) {
        String detail = ServerText.safe(cause.getMessage());
        return detail == null ? "(no detail)" : detail;
    }

    private static String required(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new EparagonyException(
                    "Server response omitted the required field '" + field + "'");
        }
        return node.asText();
    }
}
