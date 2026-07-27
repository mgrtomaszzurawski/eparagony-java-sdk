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
public record PaymentEntry(
        PaymentForm form,
        Amount amount,
        String name,
        String giftCardNo,
        Amount giftCardValue,
        String loyaltyCardNo,
        String currency,
        BigDecimal exchangeRate,
        Amount cashInOriginalValue) {

    public PaymentEntry {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(amount, "amount");
    }

    /** Starts a tender that needs more than a form and an amount. */
    public static Builder builder(PaymentForm form, Amount amount) {
        return new Builder(form, amount);
    }

    /** A tender with no finer label. */
    public static PaymentEntry of(PaymentForm form, Amount amount) {
        return builder(form, amount).build();
    }

    /** A tender labelled with its card scheme or intermediary. */
    public static PaymentEntry of(PaymentForm form, Amount amount, String name) {
        return builder(form, amount).name(name).build();
    }

    /** Builder for a tender carrying gift-card, loyalty or foreign-currency detail. */
    public static final class Builder {

        private final PaymentForm form;
        private final Amount amount;
        private String name;
        private String giftCardNo;
        private Amount giftCardValue;
        private String loyaltyCardNo;
        private String currency;
        private BigDecimal exchangeRate;
        private Amount cashInOriginalValue;

        private Builder(PaymentForm form, Amount amount) {
            this.form = Objects.requireNonNull(form, "form");
            this.amount = Objects.requireNonNull(amount, "amount");
        }

        /** The card scheme or intermediary, e.g. {@code "Visa"} or {@code "Przelewy24.pl"}. */
        public Builder name(String value) {
            this.name = Objects.requireNonNull(value, "name");
            return this;
        }

        /** The gift card used, when this tender was one. */
        public Builder giftCard(String giftCardNumber, Amount value) {
            this.giftCardNo = Objects.requireNonNull(giftCardNumber, "giftCardNo");
            this.giftCardValue = Objects.requireNonNull(value, "giftCardValue");
            return this;
        }

        /** The loyalty card presented with this tender. */
        public Builder loyaltyCardNo(String value) {
            this.loyaltyCardNo = Objects.requireNonNull(value, "loyaltyCardNo");
            return this;
        }

        /**
         * Records a tender taken in a foreign currency: what was handed over, in what currency, and
         * at what rate. {@code amount} stays the złoty equivalent, because that is what the fiscal
         * document reconciles against.
         */
        public Builder foreignCurrency(String currencyCode, BigDecimal rate, Amount originalValue) {
            this.currency = Objects.requireNonNull(currencyCode, "currency");
            this.exchangeRate = Objects.requireNonNull(rate, "exchangeRate");
            this.cashInOriginalValue = Objects.requireNonNull(originalValue, "cashInOriginalValue");
            return this;
        }

        public PaymentEntry build() {
            return new PaymentEntry(form, amount, name, giftCardNo, giftCardValue, loyaltyCardNo,
                    currency, exchangeRate, cashInOriginalValue);
        }
    }

}
