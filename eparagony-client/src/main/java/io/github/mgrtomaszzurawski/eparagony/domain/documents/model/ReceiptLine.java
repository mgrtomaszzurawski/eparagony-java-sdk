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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A single sold item on a receipt.
 *
 * <p>Two constraints are enforced by eparagony.pl before the document ever reaches the register, and
 * violating either produces a {@code 400}: the line total must equal unit price times quantity, and
 * the payments must cover the sum of the lines.
 *
 * <p>Prices are the ones <em>before</em> any discount. Discounts go in {@link #rebatesMarkups()} —
 * that is what the tax authority expects to see, and it is also what lets the buyer see what they
 * saved.
 *
 * @param productOrServiceName the name printed on the receipt. Registers truncate beyond roughly 40
 *     characters, silently.
 * @param quantity decimal quantity, e.g. {@code 1} or {@code 1.234}
 * @param unitPrice price of one unit, before discount
 * @param totalLineValue the line total; must equal {@code unitPrice × quantity}
 * @param taxRate the register's VAT slot this item falls in
 * @param unitOfMeasure optional unit label, e.g. {@code "szt."}
 * @param codes the product identifiers — EAN, SKU, PLU and the rest
 * @param storno {@code true} when this line reverses an earlier one
 * @param ticketRelief relief amount, for a transport ticket line
 * @param rebatesMarkups discounts and surcharges applied to this line
 * @param additionalDescription extra content printed beneath the line
 * @param returnPolicy return terms for this product
 * @param warranty warranty terms for this product
 */
public record ReceiptLine(
        String productOrServiceName,
        BigDecimal quantity,
        Amount unitPrice,
        Amount totalLineValue,
        TaxRateCode taxRate,
        String unitOfMeasure,
        ProductCodes codes,
        Boolean storno,
        Integer ticketRelief,
        List<RebateOrMarkup> rebatesMarkups,
        List<AdditionalDescription> additionalDescription,
        ReturnPolicy returnPolicy,
        Warranty warranty) implements ReceiptLineItem {

    public ReceiptLine {
        Objects.requireNonNull(productOrServiceName, "productOrServiceName");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(totalLineValue, "totalLineValue");
        Objects.requireNonNull(taxRate, "taxRate");
        if (productOrServiceName.isBlank()) {
            throw new IllegalArgumentException("productOrServiceName must not be blank");
        }
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive but was " + quantity);
        }
        codes = codes == null ? ProductCodes.none() : codes;
        rebatesMarkups = List.copyOf(Objects.requireNonNullElse(rebatesMarkups, List.of()));
        additionalDescription = List.copyOf(Objects.requireNonNullElse(additionalDescription, List.of()));
    }

    @Override
    public Amount contributionToTotal() {
        return totalLineValue;
    }

    /** Starts a line. */
    public static Builder builder() {
        return new Builder();
    }

    /** The EAN, if one was supplied. Shorthand for {@code codes().ean()}. */
    public String ean() {
        return codes.ean();
    }

    /** The seller's stock code, if one was supplied. */
    public String sku() {
        return codes.sku();
    }

    /** Builder for {@link ReceiptLine}. */
    public static final class Builder {

        private final List<RebateOrMarkup> rebatesMarkups = new ArrayList<>();
        private final List<AdditionalDescription> additionalDescription = new ArrayList<>();
        private final ProductCodes.Builder codes = ProductCodes.builder();
        private String productOrServiceName;
        private BigDecimal quantity = BigDecimal.ONE;
        private Amount unitPrice;
        private Amount totalLineValue;
        private TaxRateCode taxRate;
        private String unitOfMeasure;
        private Boolean storno;
        private Integer ticketRelief;
        private ReturnPolicy returnPolicy;
        private Warranty warranty;

        private Builder() {
        }

        public Builder productOrServiceName(String productName) {
            this.productOrServiceName = Objects.requireNonNull(productName, "productOrServiceName");
            return this;
        }

        public Builder quantity(BigDecimal value) {
            this.quantity = Objects.requireNonNull(value, "quantity");
            return this;
        }

        public Builder quantity(long value) {
            this.quantity = BigDecimal.valueOf(value);
            return this;
        }

        public Builder unitPrice(Amount value) {
            this.unitPrice = Objects.requireNonNull(value, "unitPrice");
            return this;
        }

        /** Sets the line total explicitly. Omit it to have {@code unitPrice × quantity} computed. */
        public Builder totalLineValue(Amount value) {
            this.totalLineValue = Objects.requireNonNull(value, "totalLineValue");
            return this;
        }

        public Builder taxRate(TaxRateCode value) {
            this.taxRate = Objects.requireNonNull(value, "taxRate");
            return this;
        }

        public Builder unitOfMeasure(String value) {
            this.unitOfMeasure = Objects.requireNonNull(value, "unitOfMeasure");
            return this;
        }

        /** Sets the EAN. Supply it when the source system has one — the API asks integrators to. */
        public Builder ean(String value) {
            codes.ean(Objects.requireNonNull(value, "ean"));
            return this;
        }

        /** Sets the seller's internal stock code. */
        public Builder sku(String value) {
            codes.sku(Objects.requireNonNull(value, "sku"));
            return this;
        }

        /** Sets the register's price-lookup code. */
        public Builder plu(String value) {
            codes.plu(Objects.requireNonNull(value, "plu"));
            return this;
        }

        /** Sets the Polish statistical classification code. */
        public Builder pkwiu(String value) {
            codes.pkwiu(Objects.requireNonNull(value, "pkwiu"));
            return this;
        }

        /** Sets the Combined Nomenclature customs code. */
        public Builder cn(String value) {
            codes.cn(Objects.requireNonNull(value, "cn"));
            return this;
        }

        /** Sets a DataMatrix code, where the goods carry one. */
        public Builder dataMatrix(String value) {
            codes.dataMatrix(Objects.requireNonNull(value, "dataMatrix"));
            return this;
        }

        /** Sets an identifier from the seller's own system. */
        public Builder externalId(String value) {
            codes.externalId(Objects.requireNonNull(value, "externalId"));
            return this;
        }

        /** Sets every product code at once. */
        public Builder codes(ProductCodes value) {
            Objects.requireNonNull(value, "codes");
            codes.ean(value.ean()).sku(value.sku()).plu(value.plu()).pkwiu(value.pkwiu())
                    .cn(value.cn()).dataMatrix(value.dataMatrix()).externalId(value.externalId());
            return this;
        }

        /** Marks this line as reversing an earlier one. */
        public Builder storno(boolean value) {
            this.storno = value;
            return this;
        }

        /** Relief amount, for a transport ticket line. */
        public Builder ticketRelief(int value) {
            this.ticketRelief = value;
            return this;
        }

        /** Adds a discount or surcharge, itemized rather than folded into the price. */
        public Builder addRebate(RebateOrMarkup rebate) {
            rebatesMarkups.add(Objects.requireNonNull(rebate, "rebate"));
            return this;
        }

        /** Adds a line of content printed beneath this item. */
        public Builder addAdditionalDescription(AdditionalDescription description) {
            additionalDescription.add(Objects.requireNonNull(description, "description"));
            return this;
        }

        /** Sets the return terms printed for this product. */
        public Builder returnPolicy(ReturnPolicy value) {
            this.returnPolicy = Objects.requireNonNull(value, "returnPolicy");
            return this;
        }

        /** Sets the warranty printed for this product. */
        public Builder warranty(Warranty value) {
            this.warranty = Objects.requireNonNull(value, "warranty");
            return this;
        }

        public ReceiptLine build() {
            Objects.requireNonNull(unitPrice, "unitPrice is required");
            Amount total = totalLineValue != null ? totalLineValue : computeTotal();
            return new ReceiptLine(productOrServiceName, quantity, unitPrice, total, taxRate,
                    unitOfMeasure, codes.build(), storno, ticketRelief,
                    rebatesMarkups, additionalDescription, returnPolicy, warranty);
        }

        /**
         * {@code unitPrice × quantity}, rejected unless it lands exactly on a grosz. Goods sold by
         * weight are where this bites: 1.5 kg at 3.33 PLN/kg is 4.995 PLN, which no fiscal document
         * can express. Rounding it silently would produce a line the server rejects for not
         * reconciling.
         */
        private Amount computeTotal() {
            BigDecimal exact = BigDecimal.valueOf(unitPrice.grosze()).multiply(quantity);
            try {
                return Amount.ofGrosze(exact.setScale(0, RoundingMode.UNNECESSARY).intValueExact());
            } catch (ArithmeticException notWhole) {
                throw new IllegalArgumentException(
                        "unitPrice " + unitPrice + " times quantity " + quantity
                                + " is not a whole number of grosze; set totalLineValue explicitly",
                        notWhole);
            }
        }
    }
}
