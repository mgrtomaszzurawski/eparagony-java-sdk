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
package io.github.mgrtomaszzurawski.eparagony.e2e;

import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.config.Environment;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.IssuedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentForm;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.RebateOrMarkup;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRebateLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterState;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live tests against the eparagony.pl sandbox. Excluded from {@code test}; run with
 * {@code ./gradlew :eparagony-client:e2eTest}.
 *
 * <p>These exist because a green WireMock suite proves only that the SDK agrees with the author's
 * belief about the wire. Everything asserted here has been observed on the real service.
 *
 * <p>The sandbox is backed by a fiscal printer emulator, and it fakes more than it admits: the device
 * number, the fiscal document number, the receipt number and the timestamp are <em>constant</em>
 * across every document it ever issues. So the assertions below deliberately check that
 * caller-supplied data round-trips and that a document reaches {@code CONFIRMED} — never that two
 * documents get different numbers, which they never will.
 */
@Tag("e2e")
class SandboxLiveTest {

    private static final String ENV_CLIENT_ID = "EPARAGONY_CLIENT_ID";
    private static final String ENV_CLIENT_SECRET = "EPARAGONY_CLIENT_SECRET";
    private static final String ENV_POS_ID = "EPARAGONY_POS_ID";

    private static final String APPLICATION_USER_AGENT =
            "EparagonyJavaSdkE2E/1.0 (+https://github.com/mgrtomaszzurawski/eparagony-java-sdk)";

    /** The emulator's device, observed in every confirmed sandbox document. */
    private static final String EMULATOR_DEVICE = "ZBN1901007833";

    private static final Duration FISCALIZATION_TIMEOUT = Duration.ofMinutes(3);
    private static final int RECEIPT_TOTAL_GROSZE = 10000;
    private static final int LINE_REBATE_GROSZE = 300;
    private static final int STANDALONE_REBATE_GROSZE = 100;

    @Test
    @DisplayName("issues a receipt and follows it through to CONFIRMED")
    void issuesAndConfirmsReceipt() {
        assumeCredentials();
        String orderId = "SDK-E2E-" + UUID.randomUUID().toString().substring(0, 12);

        try (EparagonyClient client = client(Scope.DOCUMENT_CREATE)) {
            IssuedDocument issued = client.documents().issue(receipt(orderId));

            assertNotNull(issued.documentToken());
            assertTrue(issued.documentPublicUrl().startsWith("https://"),
                    "the visualization URL must be https, it is handed to customers");
            assertTrue(issued.fiscalizationPending(),
                    "a fiscalized receipt is accepted with 202 while the register works");

            DocumentStatus status = client.documents()
                    .awaitTerminalStatus(issued.documentToken(), FISCALIZATION_TIMEOUT);

            assertEquals(DocumentState.CONFIRMED, status.state());
            // What the caller sent comes back intact — this is the part the emulator does not fake.
            assertEquals(orderId, status.orderId().orElseThrow());
            assertEquals(orderId, status.merchantDocumentId().orElseThrow());
            assertEquals(false, status.printed().orElseThrow());
            // Device-side values are present but constant on the emulator; assert presence, not value.
            assertTrue(status.fiscalDocumentId().isPresent());
            assertTrue(status.documentUrl().orElseThrow().startsWith("https://"));
        }
    }

    @Test
    @DisplayName("fiscalizes a discounted receipt, proving how the server reconciles rebates")
    void issuesDiscountedReceipt() {
        assumeCredentials();
        String orderId = "SDK-E2E-REB-" + UUID.randomUUID().toString().substring(0, 8);

        // The one thing a WireMock test cannot establish. `totalLineValue` is defined as the value
        // before discounts, so both a line's own rebatesMarkups and a standalone REBATE line have to
        // reach `grossSaleValue` — and the SDK computes that figure for the caller. Declaring it the
        // other way is rejected at submission with 400 errorCode 41, "Incorrectly calculated value of
        // 'eReceipt.metadata.grossSaleValue'", so this test going green is the proof that the SDK's
        // arithmetic matches the register's rather than merely matching its own mock.
        try (EparagonyClient client = client(Scope.DOCUMENT_CREATE)) {
            IssuedDocument issued = client.documents().issue(discountedReceipt(orderId));

            DocumentStatus status = client.documents()
                    .awaitTerminalStatus(issued.documentToken(), FISCALIZATION_TIMEOUT);

            assertEquals(DocumentState.CONFIRMED, status.state());
            assertEquals(orderId, status.orderId().orElseThrow());
        }
    }

    @Test
    @DisplayName("reads the emulator printer's status")
    void readsPrinterStatus() {
        assumeCredentials();

        try (EparagonyClient client = client(Scope.PRINTER_GET)) {
            PrinterStatus status = client.printers()
                    .status(FiscalDeviceUniqueNumber.of(EMULATOR_DEVICE));

            // assertNotNull would be vacuous: an unrecognised or absent status maps to UNKNOWN, never
            // to null. Asserting the state is one the SDK actually understands is what makes this test
            // able to fail — if the API starts reporting a state we do not model, this goes red.
            assertNotEquals(PrinterState.UNKNOWN, status.state(),
                    "the live printer state must be one this SDK models, but was UNKNOWN");
        }
    }

    @Test
    @DisplayName("fails at the token when a scope is not granted, not later with an opaque 403")
    void refusesUngrantedScope() {
        assumeCredentials();

        // document_action_get is not granted to this sandbox client. The authorization server rejects
        // it outright when scopes are space-separated — which is the whole point of sending them that
        // way rather than comma-separated as the specification says.
        try (EparagonyClient client = client(Scope.DOCUMENT_CREATE, Scope.DOCUMENT_ACTION_GET)) {
            Documents documents = client.documents();
            DocumentToken token = DocumentToken.random();

            EparagonyAuthException failure = assertThrows(EparagonyAuthException.class,
                    () -> documents.status(token));

            assertTrue(failure.getMessage().contains("document_action_get"),
                    "the failure must name the offending scope, but said: " + failure.getMessage());
        }
    }

    private static ReceiptRequest receipt(String orderId) {
        return ReceiptRequest.builder()
                .orderId(orderId)
                .merchantDocumentId(orderId)
                .fiscalize(true)
                .print(false)
                .addLine(ReceiptLine.builder()
                        .productOrServiceName("Karma sucha dla psa 1 kg")
                        .ean("05902560100679")
                        .sku("SDK-E2E-SKU")
                        .unitOfMeasure("szt.")
                        .quantity(1)
                        .unitPrice(Amount.ofGrosze(RECEIPT_TOTAL_GROSZE))
                        .taxRate(TaxRateCode.A)
                        .build())
                .addPayment(PaymentEntry.of(
                        PaymentForm.CARD, Amount.ofGrosze(RECEIPT_TOTAL_GROSZE), "Visa"))
                .build();
    }

    /**
     * The same sale carrying both discount mechanisms. The SDK derives {@code grossSaleValue} as
     * {@code 10000 - 300 - 100 = 9600}; the payment covers exactly that.
     */
    private static ReceiptRequest discountedReceipt(String orderId) {
        return ReceiptRequest.builder()
                .orderId(orderId)
                .merchantDocumentId(orderId)
                .fiscalize(true)
                .print(false)
                .addLine(ReceiptLine.builder()
                        .productOrServiceName("Karma sucha dla psa 1 kg")
                        .unitOfMeasure("szt.")
                        .quantity(1)
                        .unitPrice(Amount.ofGrosze(RECEIPT_TOTAL_GROSZE))
                        .taxRate(TaxRateCode.A)
                        .addRebate(RebateOrMarkup.rebate("Rabat na pozycji",
                                Amount.ofGrosze(LINE_REBATE_GROSZE)))
                        .build())
                .addRebateLine(ReceiptRebateLine.of("Rabat dla stalych klientow",
                        Amount.ofGrosze(STANDALONE_REBATE_GROSZE), TaxRateCode.A))
                .addPayment(PaymentEntry.of(PaymentForm.CARD,
                        Amount.ofGrosze(RECEIPT_TOTAL_GROSZE - LINE_REBATE_GROSZE
                                - STANDALONE_REBATE_GROSZE), "Visa"))
                .build();
    }

    private static EparagonyClient client(Scope... scopes) {
        return EparagonyClient.of(EparagonyConfig.builder()
                .environment(Environment.SANDBOX)
                .credentials(new ClientCredentials(
                        System.getenv(ENV_CLIENT_ID), System.getenv(ENV_CLIENT_SECRET)))
                .posId(PosId.of(System.getenv(ENV_POS_ID)))
                .scopes(scopes)
                .applicationUserAgent(APPLICATION_USER_AGENT)
                .build());
    }

    private static void assumeCredentials() {
        assumeTrue(present(ENV_CLIENT_ID) && present(ENV_CLIENT_SECRET) && present(ENV_POS_ID),
                "sandbox credentials are absent from the environment; skipping the live test");
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
