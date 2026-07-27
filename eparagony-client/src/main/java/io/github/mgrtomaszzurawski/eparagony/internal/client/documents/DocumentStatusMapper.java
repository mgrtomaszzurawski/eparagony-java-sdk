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
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonReader;


/**
 * Reads a status payload into {@link DocumentStatus}. Internal: never exported.
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
public final class DocumentStatusMapper {

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

    public static DocumentStatus fromJson(JsonNode root) {
        return DocumentStatus.builder(DocumentState.fromWireValue(JsonReader.text(root, FIELD_STATUS)))
                .documentToken(documentToken(root))
                .transactionToken(transactionToken(root))
                .documentType(JsonReader.text(root, FIELD_DOCUMENT_TYPE))
                .processingMode(JsonReader.text(root, FIELD_PROCESSING_MODE))
                .fiscalDeviceUniqueNumber(fiscalDevice(root))
                .fiscalDocumentId(JsonReader.text(root, FIELD_FISCAL_DOCUMENT_ID))
                .fiscalDocumentNumber(JsonReader.integer(root, FIELD_FISCAL_DOCUMENT_NUMBER))
                .receiptNumber(JsonReader.integer(root, FIELD_RECEIPT_NUMBER))
                .printed(JsonReader.bool(root, FIELD_PRINTED).orElse(null))
                .endTime(JsonReader.instant(root, FIELD_END_TIME))
                .orderId(JsonReader.text(root, FIELD_ORDER_ID))
                .merchantDocumentId(JsonReader.text(root, FIELD_MERCHANT_DOCUMENT_ID))
                .documentUrl(JsonReader.text(root, FIELD_DOCUMENT_URL))
                .errorMessage(errorMessage(root))
                .build();
    }

    private static DocumentToken documentToken(JsonNode root) {
        String value = JsonReader.text(root, FIELD_DOCUMENT_TOKEN);
        if (value == null) {
            return null;
        }
        try {
            return DocumentToken.of(value);
        } catch (IllegalArgumentException malformed) {
            throw malformedToken(FIELD_DOCUMENT_TOKEN, malformed);
        }
    }

    private static TransactionToken transactionToken(JsonNode root) {
        String value = JsonReader.text(root, FIELD_TRANSACTION_TOKEN);
        if (value == null) {
            return null;
        }
        try {
            return TransactionToken.of(value);
        } catch (IllegalArgumentException malformed) {
            throw malformedToken(FIELD_TRANSACTION_TOKEN, malformed);
        }
    }

    /**
     * Keeps a shape violation inside the {@code EparagonyException} hierarchy. Both callers are reads
     * — the polling endpoint and the webhook — so nothing was applied by the request that produced it.
     */
    private static EparagonyServerException malformedToken(String field,
            IllegalArgumentException cause) {
        return new EparagonyServerException(
                "Server sent a '" + field + "' this SDK cannot model: " + cause.getMessage(),
                cause, false);
    }

    private static FiscalDeviceUniqueNumber fiscalDevice(JsonNode root) {
        String value = JsonReader.text(root, FIELD_FISCAL_DEVICE);
        return value == null ? null : FiscalDeviceUniqueNumber.of(value);
    }

    private static String errorMessage(JsonNode root) {
        String explicit = JsonReader.text(root, FIELD_ERROR_MESSAGE);
        return explicit != null ? explicit : JsonReader.text(root, FIELD_MESSAGE);
    }




}
