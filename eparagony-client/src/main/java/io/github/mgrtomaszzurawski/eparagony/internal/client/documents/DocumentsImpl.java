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
import io.github.mgrtomaszzurawski.eparagony.internal.PathTemplate;
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

    private static EparagonyServerException notSettled(DocumentToken documentToken,
            DocumentStatus current, Duration timeout) {
        return new EparagonyServerException(
                "Document " + documentToken + " was still " + current.state() + " after " + timeout
                        + "; it has not failed, it has not settled yet",
                EparagonyServerException.NO_HTTP_RESPONSE, false);
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
                    text(action, FIELD_ACTION_ID),
                    ActionType.fromWireValue(text(action, FIELD_TYPE)),
                    ActionState.fromWireValue(text(action, FIELD_STATUS))));
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

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
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
        JsonNode root = codec.readTree(response.body());
        return new IssuedDocument(
                TransactionToken.of(required(root, FIELD_TRANSACTION_TOKEN)),
                DocumentToken.of(required(root, FIELD_DOCUMENT_TOKEN)),
                required(root, FIELD_DOCUMENT_PUBLIC_URL),
                required(root, FIELD_DOCUMENT_STATUS_URL),
                response.statusCode() == HTTP_ACCEPTED);
    }

    /**
     * Reads a field the specification marks required. No defensive fallback: if the server stops
     * sending one of these, that is a contract violation and it should be loud rather than silently
     * producing a document record with a hole in it.
     */
    private static String required(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new EparagonyException(
                    "Server response omitted the required field '" + field + "'");
        }
        return node.asText();
    }
}
