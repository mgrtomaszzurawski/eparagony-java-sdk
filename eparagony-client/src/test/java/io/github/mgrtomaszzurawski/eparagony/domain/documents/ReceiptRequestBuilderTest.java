package io.github.mgrtomaszzurawski.eparagony.domain.documents;

import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentForm;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The builder's reconciliation rules. These exist so that the most common cause of a rejected
 * document — amounts that do not add up — is caught in the caller's own process, with both figures
 * named, instead of coming back as HTTP 400 and a numeric error code.
 */
class ReceiptRequestBuilderTest {

    @Test
    @DisplayName("derives the line total from unit price and quantity")
    void derivesLineTotal() {
        ReceiptLine line = ReceiptLine.builder()
                .productOrServiceName("Karma")
                .quantity(new BigDecimal("2.5"))
                .unitPrice(Amount.ofGrosze(400))
                .taxRate(TaxRateCode.A)
                .build();

        assertEquals(1000, line.totalLineValue().grosze());
    }

    @Test
    @DisplayName("refuses a derived line total that is not a whole number of grosze")
    void refusesFractionalLineTotal() {
        // Goods sold by weight are where this bites: 1.5 kg at 3.33 PLN/kg is 4.995 PLN, which no
        // fiscal document can express. Rounding it silently would put a line on the receipt that does
        // not reconcile, and the server would reject the whole document with a numeric code.
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ReceiptLine.builder()
                        .productOrServiceName("Karma luzem")
                        .quantity(new BigDecimal("1.5"))
                        .unitPrice(Amount.ofGrosze(333))
                        .taxRate(TaxRateCode.A)
                        .build());

        assertTrue(failure.getMessage().contains("totalLineValue"),
                "the message must say how to resolve it, but said: " + failure.getMessage());
    }

    @Test
    @DisplayName("sums the lines and the payments when neither total is given")
    void sumsTotals() {
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(3000)))
                .addLine(line(Amount.ofGrosze(7000)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(4000)))
                .addPayment(PaymentEntry.of(PaymentForm.CARD, Amount.ofGrosze(6000)))
                .build();

        assertEquals(10000, request.grossSaleValue().grosze());
        assertEquals(10000, request.totalPaid().grosze());
    }

    @Test
    @DisplayName("rejects a declared gross value that disagrees with the lines")
    void rejectsMismatchedGrossValue() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ReceiptRequest.builder()
                        .addLine(line(Amount.ofGrosze(10000)))
                        .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                        .grossSaleValue(Amount.ofGrosze(9900))
                        .build());

        assertTrue(failure.getMessage().contains("99.00 PLN")
                        && failure.getMessage().contains("100.00 PLN"),
                "the message must name both figures, but said: " + failure.getMessage());
    }

    @Test
    @DisplayName("rejects payments that do not cover the sale")
    void rejectsUnderpayment() {
        assertThrows(IllegalArgumentException.class, () -> ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(9000)))
                .build());
    }

    @Test
    @DisplayName("accepts an overpayment, which is what change is for")
    void acceptsOverpaymentWithChange() {
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(9500)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                .change(Amount.ofGrosze(500))
                .build();

        assertEquals(500, request.changeIfPresent().orElseThrow().grosze());
    }

    @Test
    @DisplayName("rejects a receipt with no lines or no payments")
    void rejectsEmptyReceipt() {
        assertThrows(IllegalArgumentException.class, () -> ReceiptRequest.builder()
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(100)))
                .build());
        assertThrows(IllegalArgumentException.class, () -> ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(100)))
                .build());
    }

    @Test
    @DisplayName("requires every VAT slot to be declared")
    void requiresEveryTaxSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> new TaxRateTable(java.util.Map.of(TaxRateCode.A, "23")));
    }

    @Test
    @DisplayName("provides the standard Polish slot configuration")
    void providesStandardPolishRates() {
        TaxRateTable table = TaxRateTable.standardPolish();

        assertEquals("23", table.rateFor(TaxRateCode.A));
        assertEquals(TaxRateTable.EXEMPT, table.rateFor(TaxRateCode.E));
    }

    @Test
    @DisplayName("rejects a non-positive quantity")
    void rejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> ReceiptLine.builder()
                .productOrServiceName("Karma")
                .quantity(0)
                .unitPrice(Amount.ofGrosze(100))
                .taxRate(TaxRateCode.A)
                .build());
    }

    private static ReceiptLine line(Amount total) {
        return ReceiptLine.builder()
                .productOrServiceName("Karma")
                .quantity(1)
                .unitPrice(total)
                .taxRate(TaxRateCode.A)
                .build();
    }
}
