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

/**
 * How a receipt was paid for. The wire values are the Polish literals the API enumerates and are
 * reproduced verbatim; the constant names are English so calling code reads naturally.
 */
public enum PaymentForm {

    CASH("Gotówka"),
    CARD("Karta"),
    CHEQUE("Czek"),
    COUPON("Bon"),
    OTHER("Inna"),
    CREDIT("Kredyt"),
    FOREIGN_CURRENCY("Waluta obca"),
    BANK_TRANSFER("Przelew"),
    MOBILE("Mobilna"),
    VOUCHER("Voucher");

    private final String wireValue;

    PaymentForm(String wireValue) {
        this.wireValue = wireValue;
    }

    /** The literal the API expects, e.g. {@code "Karta"}. */
    public String wireValue() {
        return wireValue;
    }
}
