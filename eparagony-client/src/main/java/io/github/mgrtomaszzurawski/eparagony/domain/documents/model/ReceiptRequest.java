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
        String statusUrl,
        ReceiptMetadata metadata,
        ReceiptExtensions extensions,
        List<PackageDeposit> packageReturns,
        List<PackageDeposit> returnPackagesIssued,
        List<AdvancePaymentSettlement> settlementAdvancePayment,
        CurrencyConversion currencyExchange,
        DutyFreeSale dutyFree,
        List<AllegroDelivery> actions) {

    /** Field names used in the reconciliation failures, so the message and the builder cannot drift. */
    private static final String FIELD_TOTAL_PAID = "totalPaid";
    private static final String FIELD_GROSS_SALE_VALUE = "grossSaleValue";

    public ReceiptRequest {
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        payments = List.copyOf(Objects.requireNonNull(payments, "payments"));
        Objects.requireNonNull(totalPaid, FIELD_TOTAL_PAID);
        Objects.requireNonNull(grossSaleValue, FIELD_GROSS_SALE_VALUE);
        Objects.requireNonNull(taxRates, "taxRates");
        metadata = metadata == null ? ReceiptMetadata.none() : metadata;
        extensions = extensions == null ? ReceiptExtensions.none() : extensions;
        packageReturns = List.copyOf(Objects.requireNonNullElse(packageReturns, List.of()));
        returnPackagesIssued = List.copyOf(Objects.requireNonNullElse(returnPackagesIssued, List.of()));
        settlementAdvancePayment =
                List.copyOf(Objects.requireNonNullElse(settlementAdvancePayment, List.of()));
        actions = List.copyOf(Objects.requireNonNullElse(actions, List.of()));
        // The canonical constructor is public because records make it so. It must therefore enforce
        // the same invariants as the builder, or it becomes a documented-away back door around the
        // reconciliation this type exists to guarantee.
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a receipt must have at least one line");
        }
        if (payments.isEmpty()) {
            throw new IllegalArgumentException("a receipt must have at least one payment");
        }
        if (totalPaid.grosze() < grossSaleValue.grosze()) {
            throw new IllegalArgumentException("payments total " + totalPaid
                    + " but the declared sale value is " + grossSaleValue
                    + "; the payments must cover the sale");
        }
    }

    public static Builder builder() {
        return new Builder();
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
        private ReceiptMetadata metadata = ReceiptMetadata.none();
        private ReceiptExtensions extensions = ReceiptExtensions.none();
        private final List<PackageDeposit> packageReturns = new ArrayList<>();
        private final List<PackageDeposit> returnPackagesIssued = new ArrayList<>();
        private final List<AdvancePaymentSettlement> settlementAdvancePayment = new ArrayList<>();
        private CurrencyConversion currencyExchange;
        private DutyFreeSale dutyFree;
        private final List<AllegroDelivery> actions = new ArrayList<>();

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
            this.totalPaid = Objects.requireNonNull(value, FIELD_TOTAL_PAID);
            return this;
        }

        /** Change handed back, when any was. */
        public Builder change(Amount value) {
            this.change = Objects.requireNonNull(value, "change");
            return this;
        }

        /** Sets the declared gross sale value. Omit it to have the line totals summed. */
        public Builder grossSaleValue(Amount value) {
            this.grossSaleValue = Objects.requireNonNull(value, FIELD_GROSS_SALE_VALUE);
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

        /** Till, cashier, shift, order time and printed content. */
        public Builder metadata(ReceiptMetadata value) {
            this.metadata = Objects.requireNonNull(value, "metadata");
            return this;
        }

        /** Loyalty movements, gift cards and document-wide return or warranty terms. */
        public Builder extensions(ReceiptExtensions value) {
            this.extensions = Objects.requireNonNull(value, "extensions");
            return this;
        }

        /** Records returnable packaging the customer brought back. */
        public Builder addPackageReturn(PackageDeposit deposit) {
            packageReturns.add(Objects.requireNonNull(deposit, "deposit"));
            return this;
        }

        /** Records returnable packaging issued to the customer. */
        public Builder addReturnPackageIssued(PackageDeposit deposit) {
            returnPackagesIssued.add(Objects.requireNonNull(deposit, "deposit"));
            return this;
        }

        /** Applies an advance payment already taken against this sale. */
        public Builder addAdvancePaymentSettlement(AdvancePaymentSettlement settlement) {
            settlementAdvancePayment.add(Objects.requireNonNull(settlement, "settlement"));
            return this;
        }

        /** Prints the total restated in another currency. Informational; the fiscal total is unchanged. */
        public Builder currencyExchange(CurrencyConversion value) {
            this.currencyExchange = Objects.requireNonNull(value, "currencyExchange");
            return this;
        }

        /** Marks the sale duty-free and records the journey justifying it. */
        public Builder dutyFree(DutyFreeSale value) {
            this.dutyFree = Objects.requireNonNull(value, "dutyFree");
            return this;
        }

        /**
         * Asks eparagony.pl to deliver the issued receipt to Allegro. Runs asynchronously after
         * issuance and reports through {@code documents().actions()}.
         */
        public Builder addAction(AllegroDelivery delivery) {
            actions.add(Objects.requireNonNull(delivery, "delivery"));
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
                    FIELD_GROSS_SALE_VALUE, "the sum of the line totals");
            requireEqual(effectivePaid, paymentsTotal,
                    FIELD_TOTAL_PAID, "the sum of the individual payments");
            requireCovers(effectivePaid, linesTotal);

            return new ReceiptRequest(lines, payments, effectivePaid, change, effectiveGross, taxRates,
                    fiscalize, print, orderId, merchantDocumentId, documentToken, transactionToken,
                    statusUrl, metadata, extensions, packageReturns, returnPackagesIssued,
                    settlementAdvancePayment, currencyExchange, dutyFree, actions);
        }

        /**
         * Sums amounts, failing loudly on overflow.
         *
         * <p>{@code Math.addExact}, not {@code +}. Amounts are grosze in an {@code int}, so a total
         * above 21 474 836.47 PLN wraps to a negative number — the declared gross value would go out
         * negative and {@link #requireCovers} would then pass trivially, because any payment "covers"
         * a negative sale. A receipt that large is a data error rather than a real transaction, and it
         * should say so instead of silently producing a nonsensical document.
         */
        private static Amount sum(List<Amount> amounts) {
            int total = 0;
            for (Amount amount : amounts) {
                try {
                    total = Math.addExact(total, amount.grosze());
                } catch (ArithmeticException overflow) {
                    throw new IllegalArgumentException(
                            "receipt amounts exceed what a fiscal document can represent "
                                    + "(the running total overflowed at " + amount + ")", overflow);
                }
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
