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
package io.github.mgrtomaszzurawski.eparagony.domain.documents;

import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PackageDeposit;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentForm;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.RebateOrMarkup;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRebateLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        ReceiptLine.Builder line = ReceiptLine.builder()
                .productOrServiceName("Karma luzem")
                .quantity(new BigDecimal("1.5"))
                .unitPrice(Amount.ofGrosze(333))
                .taxRate(TaxRateCode.A);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, line::build);

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
        ReceiptRequest.Builder request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                .grossSaleValue(Amount.ofGrosze(9900));

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, request::build);

        assertTrue(failure.getMessage().contains("99.00 PLN")
                        && failure.getMessage().contains("100.00 PLN"),
                "the message must name both figures, but said: " + failure.getMessage());
    }

    @Test
    @DisplayName("rejects payments that do not cover the sale")
    void rejectsUnderpayment() {
        ReceiptRequest.Builder request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(9000)));

        assertThrows(IllegalArgumentException.class, request::build);
    }

    @Test
    @DisplayName("accepts an overpayment, which is what change is for")
    void acceptsOverpaymentWithChange() {
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(9500)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                .change(Amount.ofGrosze(500))
                .build();

        assertEquals(500, request.change().grosze());
    }

    @Test
    @DisplayName("derives the change when the caller overpays without declaring it")
    void derivesChangeOnOverpayment() {
        // The server rejects totalPaid > amountDue with no change declared, and says only
        // "errorCode 87" with an empty message. Deriving it means the caller cannot reach that.
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(9500)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                .build();

        assertEquals(500, request.change().grosze());
    }

    @Test
    @DisplayName("leaves change absent on an exact payment rather than sending a zero")
    void omitsZeroChange() {
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                .build();

        assertNull(request.change());
    }

    @Test
    @DisplayName("subtracts a returned-packaging deposit from the amount due")
    void refundsReturnedPackagingDeposit() {
        // Packaging the customer brings back is refunded, so a valid receipt is paid LESS than it
        // sold. Requiring payments to cover the sale made this unconstructible; the server accepts
        // exactly this shape and rejects the covering one.
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addPackageReturn(deposit(200))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(9800)))
                .build();

        assertEquals(10000, request.grossSaleValue().grosze());
        assertEquals(9800, request.totalPaid().grosze());
    }

    @Test
    @DisplayName("adds an issued-packaging deposit to the amount due")
    void chargesIssuedPackagingDeposit() {
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addReturnPackageIssued(deposit(500))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10500)))
                .build();

        assertEquals(10000, request.grossSaleValue().grosze());
        assertEquals(10500, request.totalPaid().grosze());
    }

    @Test
    @DisplayName("rejects a receipt whose payment ignores the packaging deposit")
    void rejectsUnbalancedPackagingDeposit() {
        ReceiptRequest.Builder request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addPackageReturn(deposit(200))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                .change(Amount.ofGrosze(0));

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, request::build);

        assertTrue(failure.getMessage().contains("98.00 PLN"),
                "the message must name the amount due, but said: " + failure.getMessage());
    }

    private static PackageDeposit deposit(int grosze) {
        return PackageDeposit.of("Butelka zwrotna 0.5l", 1, 1, Amount.ofGrosze(grosze));
    }

    @Test
    @DisplayName("rejects a receipt with no lines or no payments")
    void rejectsEmptyReceipt() {
        ReceiptRequest.Builder withoutLines = ReceiptRequest.builder()
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(100)));
        ReceiptRequest.Builder withoutPayments = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(100)));

        assertThrows(IllegalArgumentException.class, withoutLines::build);
        assertThrows(IllegalArgumentException.class, withoutPayments::build);
    }

    @Test
    @DisplayName("requires every VAT slot to be declared")
    void requiresEveryTaxSlot() {
        java.util.Map<TaxRateCode, String> incomplete = java.util.Map.of(TaxRateCode.A, "23");

        assertThrows(IllegalArgumentException.class, () -> new TaxRateTable(incomplete));
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
        ReceiptLine.Builder line = ReceiptLine.builder()
                .productOrServiceName("Karma")
                .quantity(0)
                .unitPrice(Amount.ofGrosze(100))
                .taxRate(TaxRateCode.A);

        assertThrows(IllegalArgumentException.class, line::build);
    }

    @Test
    @DisplayName("applies the specification's sign: a rebate is negative, a markup positive")
    void appliesTheSpecificationSign() {
        // The reverse of the intuitive reading, and the factories exist so a caller never has to
        // remember it. Getting this backwards puts a surcharge on a line the customer reads as a
        // discount.
        RebateOrMarkup rebate = RebateOrMarkup.rebate("Rabat", Amount.ofGrosze(500));
        RebateOrMarkup markup = RebateOrMarkup.markup("Doplata", Amount.ofGrosze(500));

        assertEquals(-500, rebate.value().grosze());
        assertEquals(500, markup.value().grosze());
        assertTrue(rebate.isRebate());
        assertFalse(markup.isRebate());
    }

    @Test
    @DisplayName("counts a standalone rebate line against the sale total")
    void countsRebateLineAgainstTheTotal() {
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addRebateLine(ReceiptRebateLine.of("Rabat -10%", Amount.ofGrosze(1000)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(9000)))
                .build();

        assertEquals(9000, request.grossSaleValue().grosze());
        assertEquals(2, request.lines().size());
    }

    @Test
    @DisplayName("counts a line's own rebates against the sale total too")
    void countsLineRebatesAgainstTheTotal() {
        // Both mechanisms reduce the sale. `totalLineValue` is defined as the value BEFORE discounts,
        // so a line discount never shows up there and `grossSaleValue` has to carry it. Getting this
        // wrong is not a rounding difference: the sandbox rejects the document outright with
        // errorCode 41, "Incorrectly calculated value of 'eReceipt.metadata.grossSaleValue'".
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(ReceiptLine.builder()
                        .productOrServiceName("Karma")
                        .quantity(1)
                        .unitPrice(Amount.ofGrosze(10000))
                        .taxRate(TaxRateCode.A)
                        .addRebate(RebateOrMarkup.rebate("Rabat na pozycji", Amount.ofGrosze(300)))
                        .build())
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(9700)))
                .build();

        assertEquals(9700, request.grossSaleValue().grosze());
    }

    @Test
    @DisplayName("reconciles the specification's own worked example")
    void reconcilesTheSpecificationExample() {
        // The "Paragon - wszystkie dane" example: a 9802 line carrying a +100 markup, a standalone
        // -100 rebate line, and a declared grossSaleValue of 9802. That figure only adds up if BOTH
        // adjustments participate, which is what makes the example a usable oracle.
        ReceiptRequest request = ReceiptRequest.builder()
                .addLine(ReceiptLine.builder()
                        .productOrServiceName("T-shirt")
                        .quantity(new BigDecimal("2"))
                        .unitPrice(Amount.ofGrosze(4901))
                        .taxRate(TaxRateCode.A)
                        .addRebate(RebateOrMarkup.markup("Red color surcharge", Amount.ofGrosze(100)))
                        .build())
                .addRebateLine(ReceiptRebateLine.of("Rabat dla stalych klientow",
                        Amount.ofGrosze(100), TaxRateCode.A))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                .grossSaleValue(Amount.ofGrosze(9802))
                .change(Amount.ofGrosze(198))
                .build();

        assertEquals(9802, request.grossSaleValue().grosze());
    }

    @Test
    @DisplayName("rejects a receipt whose rebate line is not reflected in the declared total")
    void rejectsUnreconciledRebateLine() {
        ReceiptRequest.Builder request = ReceiptRequest.builder()
                .addLine(line(Amount.ofGrosze(10000)))
                .addRebateLine(ReceiptRebateLine.of("Rabat -10%", Amount.ofGrosze(1000)))
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(10000)))
                .grossSaleValue(Amount.ofGrosze(10000));

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, request::build);

        // Naming both figures is what separates "the rebate was dropped from the sum" from "the
        // rebate was added with the wrong sign"; a bare assertThrows passes for either.
        assertTrue(failure.getMessage().contains("100.00 PLN")
                        && failure.getMessage().contains("90.00 PLN"),
                "the message must name both figures, but said: " + failure.getMessage());
    }

    @Test
    @DisplayName("carries the chosen VAT slot on a rebate line, and omits it when unset")
    void carriesTheRebateLineTaxRate() {
        // Left unset the register spreads the discount across every rate in play; set, it charges one
        // slot. Different VAT totals, so the caller has to be able to say which.
        assertEquals(TaxRateCode.A,
                ReceiptRebateLine.of("Rabat", Amount.ofGrosze(100), TaxRateCode.A).taxRate());
        assertTrue(ReceiptRebateLine.of("Rabat", Amount.ofGrosze(100))
                .taxRateIfPresent().isEmpty());
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
