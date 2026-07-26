package io.github.mgrtomaszzurawski.eparagony.internal.client.documents;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.IdempotencyKey;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.IssuedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.internal.ApiPaths;
import io.github.mgrtomaszzurawski.eparagony.internal.HttpRuntime;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonCodec;
import io.github.mgrtomaszzurawski.eparagony.internal.PathTemplate;
import io.github.mgrtomaszzurawski.eparagony.internal.RawResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Wires {@link Documents} onto the transport. Internal: never exported. */
public final class DocumentsImpl implements Documents {

    private static final String PATH_PARAM_DOCUMENT_TOKEN = "documentToken";

    private static final String FIELD_TRANSACTION_TOKEN = "transactionToken";
    private static final String FIELD_DOCUMENT_TOKEN = "documentToken";
    private static final String FIELD_DOCUMENT_PUBLIC_URL = "documentPublicUrl";
    private static final String FIELD_DOCUMENT_STATUS_URL = "documentStatusUrl";

    /** {@code 202} means the data was accepted and the register is still working. */
    private static final int HTTP_ACCEPTED = 202;

    /** How long to wait between status polls. Fiscalization takes seconds, not milliseconds. */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final HttpRuntime httpRuntime;
    private final JsonCodec codec;
    private final PosId posId;
    private final Clock clock;

    public DocumentsImpl(HttpRuntime httpRuntime, JsonCodec codec, PosId posId, Clock clock) {
        this.httpRuntime = httpRuntime;
        this.codec = codec;
        this.posId = posId;
        this.clock = clock;
    }

    @Override
    public IssuedDocument issue(ReceiptRequest request) {
        return issue(request, IdempotencyKey.random());
    }

    @Override
    public IssuedDocument issue(ReceiptRequest request, IdempotencyKey idempotencyKey) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        RawResponse response = httpRuntime.post(
                ApiPaths.DOCUMENTS, ReceiptRequestMapper.toPayload(request, posId), idempotencyKey);
        return toIssuedDocument(response);
    }

    @Override
    public DocumentStatus status(DocumentToken documentToken) {
        Objects.requireNonNull(documentToken, "documentToken");
        String path = PathTemplate.expand(
                ApiPaths.DOCUMENT_STATUS, PATH_PARAM_DOCUMENT_TOKEN, documentToken.value());
        return DocumentStatusMapper.fromJson(codec.readTree(httpRuntime.getRaw(path)));
    }

    @Override
    public DocumentStatus awaitTerminalStatus(DocumentToken documentToken, Duration timeout) {
        Objects.requireNonNull(documentToken, "documentToken");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive but was " + timeout);
        }
        Instant deadline = clock.instant().plus(timeout);
        while (true) {
            DocumentStatus current = status(documentToken);
            if (current.isTerminal()) {
                return current;
            }
            if (!clock.instant().plus(POLL_INTERVAL).isBefore(deadline)) {
                throw new EparagonyServerException(
                        "Document " + documentToken + " was still " + current.state() + " after "
                                + timeout + "; it has not failed, it has not settled yet", 0, false);
            }
            sleep();
        }
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
