package io.github.mgrtomaszzurawski.eparagony.domain.documents.model;

import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;

import java.util.Objects;
import java.util.Optional;

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

    public Optional<String> nameIfPresent() {
        return Optional.ofNullable(name);
    }
}
