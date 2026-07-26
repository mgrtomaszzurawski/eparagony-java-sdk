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
