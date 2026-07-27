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
package io.github.mgrtomaszzurawski.eparagony.domain.documents.model;

import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The current state of a document, as reported by the status endpoint or by a webhook notification.
 *
 * <p>Only {@link #state()} is always present. Which of the rest are populated depends on that state: a
 * {@code PENDING} document has no fiscal document number yet, and an {@code ERROR} one never will.
 * Rather than model five near-identical shapes, everything conditional is an {@link Optional}.
 *
 * <p>Deliberately a class rather than a record. A record would publish a second, null-returning
 * accessor beside every {@code Optional} one — and {@code receiptNumber()} returning a null
 * {@code Integer} into an {@code int} is an auto-unboxing {@code NullPointerException} waiting for its
 * first caller. One accessor per field, one representation of absence.
 */
public final class DocumentStatus {

    private final DocumentState state;
    private final DocumentToken documentToken;
    private final TransactionToken transactionToken;
    private final String documentType;
    private final String processingMode;
    private final FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber;
    private final String fiscalDocumentId;
    private final Integer fiscalDocumentNumber;
    private final Integer receiptNumber;
    private final Boolean printed;
    private final Instant endTime;
    private final String orderId;
    private final String merchantDocumentId;
    private final String documentUrl;
    private final String errorMessage;

    private DocumentStatus(Builder builder) {
        this.state = Objects.requireNonNull(builder.state, "state");
        this.documentToken = builder.documentToken;
        this.transactionToken = builder.transactionToken;
        this.documentType = builder.documentType;
        this.processingMode = builder.processingMode;
        this.fiscalDeviceUniqueNumber = builder.fiscalDeviceUniqueNumber;
        this.fiscalDocumentId = builder.fiscalDocumentId;
        this.fiscalDocumentNumber = builder.fiscalDocumentNumber;
        this.receiptNumber = builder.receiptNumber;
        this.printed = builder.printed;
        this.endTime = builder.endTime;
        this.orderId = builder.orderId;
        this.merchantDocumentId = builder.merchantDocumentId;
        this.documentUrl = builder.documentUrl;
        this.errorMessage = builder.errorMessage;
    }

    /**
     * Starts a status. Assembled by the SDK from a server payload; consumers receive one rather than
     * building one, but the type is public because a consumer's own tests may want to fabricate one.
     *
     * <p>A builder rather than a fifteen-argument constructor: fourteen of the fifteen are optional
     * and most are the same type, so a positional call is a transposition waiting to happen.
     */
    public static Builder builder(DocumentState state) {
        return new Builder(state);
    }

    /** Where the document has got to. Never null. */
    public DocumentState state() {
        return state;
    }

    /** {@code true} when the document reached the repository and may be shown to the customer. */
    public boolean isConfirmed() {
        return state == DocumentState.CONFIRMED;
    }

    /** {@code true} when no further transition is expected. */
    public boolean isTerminal() {
        return state.isTerminal();
    }

    public Optional<DocumentToken> documentToken() {
        return Optional.ofNullable(documentToken);
    }

    public Optional<TransactionToken> transactionToken() {
        return Optional.ofNullable(transactionToken);
    }

    /** The kind of document, e.g. {@code RECEIPT}, as the server labels it. */
    public Optional<String> documentType() {
        return Optional.ofNullable(documentType);
    }

    /** How it is being processed, e.g. {@code FISCALIZATION} or {@code NONE}. */
    public Optional<String> processingMode() {
        return Optional.ofNullable(processingMode);
    }

    /** The register that issued it, once fiscalized. */
    public Optional<FiscalDeviceUniqueNumber> fiscalDeviceUniqueNumber() {
        return Optional.ofNullable(fiscalDeviceUniqueNumber);
    }

    /** The register-scoped identifier, e.g. {@code ZBN1901007833/570}. */
    public Optional<String> fiscalDocumentId() {
        return Optional.ofNullable(fiscalDocumentId);
    }

    public Optional<Integer> fiscalDocumentNumber() {
        return Optional.ofNullable(fiscalDocumentNumber);
    }

    public Optional<Integer> receiptNumber() {
        return Optional.ofNullable(receiptNumber);
    }

    /** Whether paper was produced. Absent means the server did not say, which is not the same as no. */
    public Optional<Boolean> printed() {
        return Optional.ofNullable(printed);
    }

    /** When the register completed the document. Constant on the sandbox emulator; never assert on it. */
    public Optional<Instant> endTime() {
        return Optional.ofNullable(endTime);
    }

    public Optional<String> orderId() {
        return Optional.ofNullable(orderId);
    }

    public Optional<String> merchantDocumentId() {
        return Optional.ofNullable(merchantDocumentId);
    }

    /** The customer-facing visualization page. */
    public Optional<String> documentUrl() {
        return Optional.ofNullable(documentUrl);
    }

    /** The failure detail, when {@link #state()} is {@link DocumentState#ERROR}. */
    public Optional<String> errorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    /** Builder for {@link DocumentStatus}. */
    public static final class Builder {

        private final DocumentState state;
        private DocumentToken documentToken;
        private TransactionToken transactionToken;
        private String documentType;
        private String processingMode;
        private FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber;
        private String fiscalDocumentId;
        private Integer fiscalDocumentNumber;
        private Integer receiptNumber;
        private Boolean printed;
        private Instant endTime;
        private String orderId;
        private String merchantDocumentId;
        private String documentUrl;
        private String errorMessage;

        private Builder(DocumentState state) {
            this.state = Objects.requireNonNull(state, "state");
        }

        public Builder documentToken(DocumentToken value) {
            this.documentToken = value;
            return this;
        }

        public Builder transactionToken(TransactionToken value) {
            this.transactionToken = value;
            return this;
        }

        public Builder documentType(String value) {
            this.documentType = value;
            return this;
        }

        public Builder processingMode(String value) {
            this.processingMode = value;
            return this;
        }

        public Builder fiscalDeviceUniqueNumber(FiscalDeviceUniqueNumber value) {
            this.fiscalDeviceUniqueNumber = value;
            return this;
        }

        public Builder fiscalDocumentId(String value) {
            this.fiscalDocumentId = value;
            return this;
        }

        public Builder fiscalDocumentNumber(Integer value) {
            this.fiscalDocumentNumber = value;
            return this;
        }

        public Builder receiptNumber(Integer value) {
            this.receiptNumber = value;
            return this;
        }

        public Builder printed(Boolean value) {
            this.printed = value;
            return this;
        }

        public Builder endTime(Instant value) {
            this.endTime = value;
            return this;
        }

        public Builder orderId(String value) {
            this.orderId = value;
            return this;
        }

        public Builder merchantDocumentId(String value) {
            this.merchantDocumentId = value;
            return this;
        }

        public Builder documentUrl(String value) {
            this.documentUrl = value;
            return this;
        }

        public Builder errorMessage(String value) {
            this.errorMessage = value;
            return this;
        }

        public DocumentStatus build() {
            return new DocumentStatus(this);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DocumentStatus status)) {
            return false;
        }
        return state == status.state
                && Objects.equals(documentToken, status.documentToken)
                && Objects.equals(transactionToken, status.transactionToken)
                && Objects.equals(documentType, status.documentType)
                && Objects.equals(processingMode, status.processingMode)
                && Objects.equals(fiscalDeviceUniqueNumber, status.fiscalDeviceUniqueNumber)
                && Objects.equals(fiscalDocumentId, status.fiscalDocumentId)
                && Objects.equals(fiscalDocumentNumber, status.fiscalDocumentNumber)
                && Objects.equals(receiptNumber, status.receiptNumber)
                && Objects.equals(printed, status.printed)
                && Objects.equals(endTime, status.endTime)
                && Objects.equals(orderId, status.orderId)
                && Objects.equals(merchantDocumentId, status.merchantDocumentId)
                && Objects.equals(documentUrl, status.documentUrl)
                && Objects.equals(errorMessage, status.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, documentToken, transactionToken, documentType, processingMode,
                fiscalDeviceUniqueNumber, fiscalDocumentId, fiscalDocumentNumber, receiptNumber,
                printed, endTime, orderId, merchantDocumentId, documentUrl, errorMessage);
    }

    /** Renders the fields worth seeing in a log line. Carries no personal data. */
    @Override
    public String toString() {
        return "DocumentStatus[state=" + state
                + ", documentToken=" + documentToken
                + ", documentType=" + documentType
                + ", fiscalDocumentId=" + fiscalDocumentId + "]";
    }
}
