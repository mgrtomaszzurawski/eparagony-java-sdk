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

import java.util.Objects;
import java.util.Optional;

/**
 * A standalone discount line — a reduction applied to part or all of the sale rather than to one
 * product.
 *
 * <p>The sign follows the specification, which is the opposite of the intuitive reading: a
 * <em>negative</em> value reduces the sale. {@link #of} takes the magnitude and applies the sign, so
 * a caller never has to remember that.
 *
 * @param name what to print beside it, e.g. {@code "Rabat -10% na cały paragon"}
 * @param value signed per the specification: negative reduces the sale
 * @param taxRate the VAT slot to charge the discount against, or {@code null} to let the register
 *     distribute it proportionally across every rate on the receipt
 */
public record ReceiptRebateLine(String name, Amount value, TaxRateCode taxRate)
        implements ReceiptLineItem {

    /** The discriminator this branch carries in the line list. */
    public static final String LINE_TYPE = "REBATE";

    public ReceiptRebateLine {
        Objects.requireNonNull(value, "value");
    }

    /**
     * A receipt-wide discount of the given magnitude, distributed proportionally across every tax
     * rate on the receipt. Pass a positive amount — the negative sign is applied here.
     */
    public static ReceiptRebateLine of(String name, Amount magnitude) {
        return of(name, magnitude, null);
    }

    /**
     * A discount of the given magnitude charged against one VAT slot. Pass a positive amount.
     *
     * <p>Which slot a discount lands in is a fiscal decision, not a formatting one: left unset the
     * register spreads it across every rate in play, which produces different VAT totals from
     * charging it wholly to one.
     */
    public static ReceiptRebateLine of(String name, Amount magnitude, TaxRateCode taxRate) {
        return new ReceiptRebateLine(name, Amount.ofGrosze(-Math.absExact(magnitude.grosze())),
                taxRate);
    }

    /**
     * A receipt-wide <em>surcharge</em> of the given magnitude. The same wire shape as {@link #of}
     * with the opposite sign — the specification models both through one {@code REBATE} line.
     */
    public static ReceiptRebateLine markup(String name, Amount magnitude) {
        return markup(name, magnitude, null);
    }

    /** A surcharge charged against one VAT slot. Pass a positive amount. */
    public static ReceiptRebateLine markup(String name, Amount magnitude, TaxRateCode taxRate) {
        return new ReceiptRebateLine(name, Amount.ofGrosze(Math.absExact(magnitude.grosze())),
                taxRate);
    }

    /** What to print, when the caller supplied it. Optional per the specification. */
    public Optional<String> nameIfPresent() {
        return Optional.ofNullable(name);
    }

    /** The VAT slot charged, when one was chosen rather than left to proportional distribution. */
    public Optional<TaxRateCode> taxRateIfPresent() {
        return Optional.ofNullable(taxRate);
    }

    @Override
    public Amount contributionToTotal() {
        return value;
    }
}
