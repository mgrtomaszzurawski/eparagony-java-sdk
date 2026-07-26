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

import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A request to issue a fiscal e-receipt.
 *
 * <p>Build one with {@link #builder()}. The builder reconciles the amounts before the request leaves
 * the process: the payments must cover the lines, and the declared gross sale value must equal their
 * sum. eparagony.pl enforces both and answers {@code 400}, so checking here turns a round trip and an
 * opaque error code into an exception naming the two figures that disagree.
 */
public record ReceiptRequest(
        List<ReceiptLine> lines,
        List<PaymentEntry> payments,
        Amount totalPaid,
        Amount change,
        Amount grossSaleValue,
        TaxRateTable taxRates,
        boolean fiscalize,
        boolean print,
        String orderId,
        String merchantDocumentId,
        DocumentToken documentToken,
        TransactionToken transactionToken,
        String statusUrl) {

    public ReceiptRequest {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        payments = List.copyOf(Objects.requireNonNull(payments, "payments"));
        Objects.requireNonNull(totalPaid, "totalPaid");
        Objects.requireNonNull(grossSaleValue, "grossSaleValue");
        Objects.requireNonNull(taxRates, "taxRates");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Amount> changeIfPresent() {
        return Optional.ofNullable(change);
    }

    public Optional<String> orderIdIfPresent() {
        return Optional.ofNullable(orderId);
    }

    public Optional<String> merchantDocumentIdIfPresent() {
        return Optional.ofNullable(merchantDocumentId);
    }

    public Optional<DocumentToken> documentTokenIfPresent() {
        return Optional.ofNullable(documentToken);
    }

    public Optional<TransactionToken> transactionTokenIfPresent() {
        return Optional.ofNullable(transactionToken);
    }

    public Optional<String> statusUrlIfPresent() {
        return Optional.ofNullable(statusUrl);
    }

    /** Builder for {@link ReceiptRequest}. */
    public static final class Builder {

        private final List<ReceiptLine> lines = new ArrayList<>();
        private final List<PaymentEntry> payments = new ArrayList<>();
        private Amount totalPaid;
        private Amount change;
        private Amount grossSaleValue;
        private TaxRateTable taxRates = TaxRateTable.standardPolish();
        private boolean fiscalize = true;
        private boolean print;
        private String orderId;
        private String merchantDocumentId;
        private DocumentToken documentToken;
        private TransactionToken transactionToken;
        private String statusUrl;

        private Builder() {
        }

        public Builder addLine(ReceiptLine line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder lines(List<ReceiptLine> values) {
            lines.clear();
            lines.addAll(Objects.requireNonNull(values, "lines"));
            return this;
        }

        public Builder addPayment(PaymentEntry payment) {
            payments.add(Objects.requireNonNull(payment, "payment"));
            return this;
        }

        /** Sets the total tendered. Omit it to have the payments summed. */
        public Builder totalPaid(Amount value) {
            this.totalPaid = Objects.requireNonNull(value, "totalPaid");
            return this;
        }

        /** Change handed back, when any was. */
        public Builder change(Amount value) {
            this.change = Objects.requireNonNull(value, "change");
            return this;
        }

        /** Sets the declared gross sale value. Omit it to have the line totals summed. */
        public Builder grossSaleValue(Amount value) {
            this.grossSaleValue = Objects.requireNonNull(value, "grossSaleValue");
            return this;
        }

        /** The register's VAT slot configuration. Defaults to {@link TaxRateTable#standardPolish()}. */
        public Builder taxRates(TaxRateTable value) {
            this.taxRates = Objects.requireNonNull(value, "taxRates");
            return this;
        }

        /**
         * Whether the cash register should issue a fiscal document. Defaults to {@code true}, which is
         * the point of the API; {@code false} produces a non-fiscal document only.
         */
        public Builder fiscalize(boolean value) {
            this.fiscalize = value;
            return this;
        }

        /** Whether the printer should also produce paper. Defaults to {@code false}. */
        public Builder print(boolean value) {
            this.print = value;
            return this;
        }

        /** The order number the customer knows. Supply it wherever one exists — the API asks for it. */
        public Builder orderId(String value) {
            this.orderId = Objects.requireNonNull(value, "orderId");
            return this;
        }

        /** The seller's own document number. */
        public Builder merchantDocumentId(String value) {
            this.merchantDocumentId = Objects.requireNonNull(value, "merchantDocumentId");
            return this;
        }

        /** Fixes the document identifier instead of letting the server mint one. */
        public Builder documentToken(DocumentToken value) {
            this.documentToken = Objects.requireNonNull(value, "documentToken");
            return this;
        }

        /**
         * Ties this document to a transaction. Required when reissuing after a fiscalization error:
         * keep the original transaction token and take a fresh {@link DocumentToken}.
         */
        public Builder transactionToken(TransactionToken value) {
            this.transactionToken = Objects.requireNonNull(value, "transactionToken");
            return this;
        }

        /** Where eparagony.pl should POST the fiscalization status notification. */
        public Builder statusUrl(String value) {
            this.statusUrl = Objects.requireNonNull(value, "statusUrl");
            return this;
        }

        public ReceiptRequest build() {
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("a receipt must have at least one line");
            }
            if (payments.isEmpty()) {
                throw new IllegalArgumentException("a receipt must have at least one payment");
            }
            Amount linesTotal = sum(lines.stream().map(ReceiptLine::totalLineValue).toList());
            Amount paymentsTotal = sum(payments.stream().map(PaymentEntry::amount).toList());
            Amount effectiveGross = grossSaleValue != null ? grossSaleValue : linesTotal;
            Amount effectivePaid = totalPaid != null ? totalPaid : paymentsTotal;

            requireEqual(effectiveGross, linesTotal,
                    "grossSaleValue", "the sum of the line totals");
            requireEqual(effectivePaid, paymentsTotal,
                    "totalPaid", "the sum of the individual payments");
            requireCovers(effectivePaid, linesTotal);

            return new ReceiptRequest(lines, payments, effectivePaid, change, effectiveGross, taxRates,
                    fiscalize, print, orderId, merchantDocumentId, documentToken, transactionToken,
                    statusUrl);
        }

        private static Amount sum(List<Amount> amounts) {
            int total = 0;
            for (Amount amount : amounts) {
                total += amount.grosze();
            }
            return Amount.ofGrosze(total);
        }

        private static void requireEqual(Amount declared, Amount computed, String declaredName,
                String computedName) {
            if (declared.grosze() != computed.grosze()) {
                throw new IllegalArgumentException(declaredName + " is " + declared + " but "
                        + computedName + " is " + computed
                        + "; eparagony.pl rejects a receipt whose amounts do not reconcile");
            }
        }

        private static void requireCovers(Amount paid, Amount linesTotal) {
            if (paid.grosze() < linesTotal.grosze()) {
                throw new IllegalArgumentException("payments total " + paid
                        + " but the sold items total " + linesTotal
                        + "; the payments must cover the sale");
            }
        }
    }
}
