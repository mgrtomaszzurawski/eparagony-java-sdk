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
 * Settlement of an advance payment already taken, applied against this receipt.
 *
 * <p>The customer paid something up front; this line accounts for it at the till so the receipt shows
 * what is still owed rather than charging them twice.
 *
 * @param nameOfPayment what to print, e.g. {@code "Zaliczka 2026/04/17"}
 * @param value the advance being settled
 * @param taxRate the VAT slot it was taken under
 * @param requiredAdditionalPayment what remains payable after the advance is applied
 * @param storno {@code true} when this reverses an earlier settlement
 */
public record AdvancePaymentSettlement(
        String nameOfPayment,
        Amount value,
        TaxRateCode taxRate,
        Amount requiredAdditionalPayment,
        Boolean storno) {

    public AdvancePaymentSettlement {
        Objects.requireNonNull(nameOfPayment, "nameOfPayment");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(taxRate, "taxRate");
    }

    /** An advance settled in full, with nothing further to pay against it. */
    public static AdvancePaymentSettlement of(String nameOfPayment, Amount value, TaxRateCode taxRate) {
        return new AdvancePaymentSettlement(nameOfPayment, value, taxRate, null, null);
    }

    /** The same settlement, stating what the customer still owes. */
    public AdvancePaymentSettlement withOutstanding(Amount outstanding) {
        return new AdvancePaymentSettlement(nameOfPayment, value, taxRate,
                Objects.requireNonNull(outstanding, "requiredAdditionalPayment"), storno);
    }

    /** The same settlement, marked as reversing an earlier one. */
    public AdvancePaymentSettlement reversing() {
        return new AdvancePaymentSettlement(nameOfPayment, value, taxRate, requiredAdditionalPayment,
                Boolean.TRUE);
    }

    public Optional<Amount> requiredAdditionalPaymentIfPresent() {
        return Optional.ofNullable(requiredAdditionalPayment);
    }

    public Optional<Boolean> stornoIfPresent() {
        return Optional.ofNullable(storno);
    }
}
