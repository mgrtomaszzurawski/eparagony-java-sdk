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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The context around a receipt: which till, which cashier, which shift, and what to print alongside.
 *
 * <p>None of it is required, and all of it is worth supplying. The register can be programmed with
 * its own till and cashier identifiers — and where it is, that programming wins over anything sent
 * here. Where it is not, eparagony.pl substitutes defaults, so the values below are how a
 * multi-till integration keeps its receipts attributable.
 *
 * @param cashRegisterId the till, as the seller identifies it
 * @param cashierId the cashier on duty
 * @param shiftId the shift
 * @param orderTime when the customer placed the order, as opposed to when it was fiscalized
 * @param currency the register's accounting currency, ISO-4217; absent means the register's default
 * @param consumerTIN the buyer's tax identification number, when they asked for it on the receipt
 * @param additionalDescription content printed on the document — advertising, notices, QR codes
 */
public record ReceiptMetadata(
        String cashRegisterId,
        String cashierId,
        String shiftId,
        Instant orderTime,
        String currency,
        String consumerTIN,
        List<ContentLine> additionalDescription) {

    private static final ReceiptMetadata EMPTY =
            new ReceiptMetadata(null, null, null, null, null, null, List.of());

    public ReceiptMetadata {
        additionalDescription = List.copyOf(Objects.requireNonNullElse(additionalDescription, List.of()));
    }

    /** No metadata beyond what the receipt itself carries. */
    public static ReceiptMetadata none() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** {@code true} when nothing at all was supplied, so the object can be omitted from the wire. */
    public boolean isEmpty() {
        return cashRegisterId == null && cashierId == null && shiftId == null && orderTime == null
                && currency == null && consumerTIN == null && additionalDescription.isEmpty();
    }

    /** Builder for {@link ReceiptMetadata}. */
    public static final class Builder {

        private final List<ContentLine> additionalDescription = new ArrayList<>();
        private String cashRegisterId;
        private String cashierId;
        private String shiftId;
        private Instant orderTime;
        private String currency;
        private String consumerTIN;

        private Builder() {
        }

        /** Identifies the till. Ignored if the register is programmed with its own. */
        public Builder cashRegisterId(String value) {
            this.cashRegisterId = Objects.requireNonNull(value, "cashRegisterId");
            return this;
        }

        /** Identifies the cashier. Ignored if the register is programmed with its own. */
        public Builder cashierId(String value) {
            this.cashierId = Objects.requireNonNull(value, "cashierId");
            return this;
        }

        public Builder shiftId(String value) {
            this.shiftId = Objects.requireNonNull(value, "shiftId");
            return this;
        }

        /** When the customer placed the order — often well before fiscalization in e-commerce. */
        public Builder orderTime(Instant value) {
            this.orderTime = Objects.requireNonNull(value, "orderTime");
            return this;
        }

        /** The register's accounting currency, ISO-4217, e.g. {@code "PLN"}. */
        public Builder currency(String value) {
            this.currency = Objects.requireNonNull(value, "currency");
            return this;
        }

        /**
         * The buyer's tax identification number, printed when they ask for it.
         *
         * <p>A Polish NIP here turns a receipt into one an invoice can later be issued against, which
         * is a decision with tax consequences — supply it only when the buyer actually requested it.
         */
        public Builder consumerTIN(String value) {
            this.consumerTIN = Objects.requireNonNull(value, "consumerTIN");
            return this;
        }

        /** Adds a line of seller-defined content printed on the document. */
        public Builder addAdditionalDescription(ContentLine description) {
            additionalDescription.add(Objects.requireNonNull(description, "description"));
            return this;
        }

        public ReceiptMetadata build() {
            return new ReceiptMetadata(cashRegisterId, cashierId, shiftId, orderTime, currency,
                    consumerTIN, additionalDescription);
        }
    }
}
