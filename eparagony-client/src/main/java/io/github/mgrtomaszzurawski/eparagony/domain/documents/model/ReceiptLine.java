package io.github.mgrtomaszzurawski.eparagony.domain.documents.model;

import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A single sold item on a receipt.
 *
 * <p>Two constraints are enforced by eparagony.pl before the document ever reaches the register, and
 * violating either produces a {@code 400}: the line total must equal unit price times quantity, and
 * the payments must cover the sum of the lines.
 *
 * <p>Prices are the ones <em>before</em> any discount. Discounts are itemized separately — that is
 * what the tax authority expects to see, and it is also what lets the buyer see what they saved.
 *
 * @param productOrServiceName the name printed on the receipt. Registers truncate beyond roughly 40
 *     characters, silently.
 * @param quantity decimal quantity, e.g. {@code 1} or {@code 1.234}
 * @param unitPrice price of one unit, before discount
 * @param totalLineValue the line total; must equal {@code unitPrice × quantity}
 * @param taxRate the register's VAT slot this item falls in
 * @param unitOfMeasure optional unit label, e.g. {@code "szt."}
 * @param ean optional EAN barcode
 * @param sku optional seller-internal stock code
 */
public record ReceiptLine(
        String productOrServiceName,
        BigDecimal quantity,
        Amount unitPrice,
        Amount totalLineValue,
        TaxRateCode taxRate,
        String unitOfMeasure,
        String ean,
        String sku) {

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
    }

    /** Starts a line. */
    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> unitOfMeasureIfPresent() {
        return Optional.ofNullable(unitOfMeasure);
    }

    public Optional<String> eanIfPresent() {
        return Optional.ofNullable(ean);
    }

    public Optional<String> skuIfPresent() {
        return Optional.ofNullable(sku);
    }

    /** Builder for {@link ReceiptLine}. */
    public static final class Builder {

        private String productOrServiceName;
        private BigDecimal quantity = BigDecimal.ONE;
        private Amount unitPrice;
        private Amount totalLineValue;
        private TaxRateCode taxRate;
        private String unitOfMeasure;
        private String ean;
        private String sku;

        private Builder() {
        }

        public Builder productOrServiceName(String value) {
            this.productOrServiceName = Objects.requireNonNull(value, "productOrServiceName");
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
            this.ean = Objects.requireNonNull(value, "ean");
            return this;
        }

        /** Sets the seller's internal stock code. */
        public Builder sku(String value) {
            this.sku = Objects.requireNonNull(value, "sku");
            return this;
        }

        public ReceiptLine build() {
            Objects.requireNonNull(unitPrice, "unitPrice is required");
            Amount total = totalLineValue != null ? totalLineValue : computeTotal();
            return new ReceiptLine(productOrServiceName, quantity, unitPrice, total, taxRate,
                    unitOfMeasure, ean, sku);
        }

        /**
         * {@code unitPrice × quantity}, rejected unless it lands exactly on a grosz. A quantity such
         * as {@code 0.333} against an odd unit price cannot be represented, and rounding it here would
         * produce a line the server rejects for not reconciling — better to say so precisely.
         */
        private Amount computeTotal() {
            BigDecimal exact = BigDecimal.valueOf(unitPrice.grosze()).multiply(quantity);
            try {
                return Amount.ofGrosze(exact.setScale(0, java.math.RoundingMode.UNNECESSARY).intValueExact());
            } catch (ArithmeticException notWhole) {
                throw new IllegalArgumentException(
                        "unitPrice " + unitPrice + " times quantity " + quantity
                                + " is not a whole number of grosze; set totalLineValue explicitly",
                        notWhole);
            }
        }
    }
}
