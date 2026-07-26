package io.github.mgrtomaszzurawski.eparagony.internal.client.documents;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Reads a status payload into {@link DocumentStatus}. Package-private.
 *
 * <p>Parsed from the JSON tree rather than through a generated model. The specification declares this
 * response as a {@code oneOf} across document kinds, each with a nested {@code discriminator} on
 * {@code status}, and the field sets overlap heavily — resolving that through generated polymorphic
 * types would produce a large family of classes to express what is really one flat, mostly-optional
 * shape. Reading the tree also means an unfamiliar status or an added field cannot break decoding,
 * which matters given the API's stated freedom to add fields without warning.
 *
 * <p>The same mapper serves both channels: the polling endpoint and the webhook notification carry
 * the same shape, differing only in which states they can express.
 */
final class DocumentStatusMapper {

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DOCUMENT_TOKEN = "documentToken";
    private static final String FIELD_TRANSACTION_TOKEN = "transactionToken";
    private static final String FIELD_DOCUMENT_TYPE = "documentType";
    private static final String FIELD_PROCESSING_MODE = "processingMode";
    private static final String FIELD_FISCAL_DEVICE = "fiscalDeviceUniqueNumber";
    private static final String FIELD_FISCAL_DOCUMENT_ID = "fiscalDocumentId";
    private static final String FIELD_FISCAL_DOCUMENT_NUMBER = "fiscalDocumentNumber";
    private static final String FIELD_RECEIPT_NUMBER = "receiptNumber";
    private static final String FIELD_PRINTED = "printed";
    private static final String FIELD_END_TIME = "endTime";
    private static final String FIELD_ORDER_ID = "orderId";
    private static final String FIELD_MERCHANT_DOCUMENT_ID = "merchantDocumentId";
    private static final String FIELD_DOCUMENT_URL = "documentUrl";
    private static final String FIELD_ERROR_MESSAGE = "errorMessage";
    private static final String FIELD_MESSAGE = "message";

    private DocumentStatusMapper() {
    }

    static DocumentStatus fromJson(JsonNode root) {
        return new DocumentStatus(
                DocumentState.fromWireValue(text(root, FIELD_STATUS)),
                documentToken(root),
                transactionToken(root),
                text(root, FIELD_DOCUMENT_TYPE),
                text(root, FIELD_PROCESSING_MODE),
                fiscalDevice(root),
                text(root, FIELD_FISCAL_DOCUMENT_ID),
                integer(root, FIELD_FISCAL_DOCUMENT_NUMBER),
                integer(root, FIELD_RECEIPT_NUMBER),
                bool(root, FIELD_PRINTED),
                instant(root),
                text(root, FIELD_ORDER_ID),
                text(root, FIELD_MERCHANT_DOCUMENT_ID),
                text(root, FIELD_DOCUMENT_URL),
                errorMessage(root));
    }

    private static DocumentToken documentToken(JsonNode root) {
        String value = text(root, FIELD_DOCUMENT_TOKEN);
        return value == null ? null : DocumentToken.of(value);
    }

    private static TransactionToken transactionToken(JsonNode root) {
        String value = text(root, FIELD_TRANSACTION_TOKEN);
        return value == null ? null : TransactionToken.of(value);
    }

    private static FiscalDeviceUniqueNumber fiscalDevice(JsonNode root) {
        String value = text(root, FIELD_FISCAL_DEVICE);
        return value == null ? null : FiscalDeviceUniqueNumber.of(value);
    }

    private static String errorMessage(JsonNode root) {
        String explicit = text(root, FIELD_ERROR_MESSAGE);
        return explicit != null ? explicit : text(root, FIELD_MESSAGE);
    }

    private static Instant instant(JsonNode root) {
        String value = text(root, FIELD_END_TIME);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException unparseable) {
            // A timestamp the SDK cannot read is not worth failing a fiscal confirmation over.
            return null;
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private static Integer integer(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private static Boolean bool(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isBoolean() ? node.asBoolean() : null;
    }
}
