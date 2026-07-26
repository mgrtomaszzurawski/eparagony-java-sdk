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

/**
 * One tender against a receipt. A receipt may be settled with several.
 *
 * @param form the payment form
 * @param amount the amount settled by this tender
 * @param name an optional finer label — the card scheme ({@code "Visa"}) or the intermediary
 *     ({@code "Przelewy24.pl"}). Worth supplying: on Elzab printers, an absent name causes the
 *     electronic receipt to fall back to the form's own label.
 */
public record PaymentEntry(PaymentForm form, Amount amount, String name) {

    public PaymentEntry {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(amount, "amount");
    }

    /** A tender with no finer label. */
    public static PaymentEntry of(PaymentForm form, Amount amount) {
        return new PaymentEntry(form, amount, null);
    }

    /** A tender labelled with its card scheme or intermediary. */
    public static PaymentEntry of(PaymentForm form, Amount amount, String name) {
        return new PaymentEntry(form, amount, Objects.requireNonNull(name, "name"));
    }

}
