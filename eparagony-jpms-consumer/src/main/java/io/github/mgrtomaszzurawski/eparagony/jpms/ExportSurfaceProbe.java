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
package io.github.mgrtomaszzurawski.eparagony.jpms;

import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.config.Environment;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyRateLimitException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyValidationException;
import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.core.model.IdempotencyKey;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.core.retry.BackoffStrategy;
import io.github.mgrtomaszzurawski.eparagony.core.retry.RetryPolicy;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.WebhookSecret;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.WebhookVerifier;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.ActionStatusNotification;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.DocumentStatusNotification;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.WebhookNotifications;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentAction;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.IssuedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentForm;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRebateLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.SignedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateTable;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.Printers;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.DailyReport;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterState;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Touches every type a consumer is meant to reach, so that a missing {@code exports} line breaks the
 * build. Nothing here runs; it only has to compile on the module path.
 *
 * <p>The negative half of the gate is equally important and is asserted by absence: this file must
 * never import anything from {@code ...eparagony.internal} or {@code ...eparagony.rest.model}. If
 * such an import ever compiles, the module boundary has been breached.
 */
public final class ExportSurfaceProbe {

    private ExportSurfaceProbe() {
    }

    /** Exercises configuration, retry and the entry point. */
    public static EparagonyConfig configuration() {
        return EparagonyConfig.builder()
                .environment(Environment.SANDBOX)
                .credentials(new ClientCredentials("client-id", "client-secret"))
                .posId(PosId.of("pos"))
                .scopes(Scope.DOCUMENT_CREATE, Scope.PRINTER_GET)
                .applicationUserAgent("JpmsConsumerProbe/1.0 (+https://example.test)")
                .integrationId("integration")
                .requestTimeout(Duration.ofSeconds(30))
                .retryPolicy(RetryPolicy.builder()
                        .backoffStrategy(BackoffStrategy.EXPONENTIAL)
                        .maxAttempts(3)
                        .retryPost(false)
                        .build())
                .build();
    }

    /** Exercises the document facade and its model. */
    public static void documents(EparagonyClient client) {
        ReceiptRequest request = ReceiptRequest.builder()
                .taxRates(TaxRateTable.standardPolish().with(TaxRateCode.B, "8"))
                .addLine(ReceiptLine.builder()
                        .productOrServiceName("Item")
                        .quantity(1)
                        .unitPrice(Amount.ofGrosze(100))
                        .taxRate(TaxRateCode.A)
                        .ean("05902560100679")
                        .sku("SKU-1")
                        .unitOfMeasure("szt.")
                        .build())
                .addRebateLine(ReceiptRebateLine.of("Rabat", Amount.ofGrosze(10)))
                .addPayment(PaymentEntry.of(PaymentForm.CARD, Amount.ofGrosze(90), "Visa"))
                .statusUrl("https://example.test/webhook")
                .build();

        Documents documents = client.documents();
        IssuedDocument issued = documents.issue(request, IdempotencyKey.random());
        DocumentStatus status = documents.awaitTerminalStatus(issued.documentToken(), Duration.ofMinutes(1));
        DocumentState state = status.state();
        status.documentUrl().ifPresent(url -> consume(url + state));
        documents.status(DocumentToken.random());
        for (DocumentAction action : documents.actions(issued.documentToken())) {
            consume(action.actionId() + action.type() + action.state() + action.isCompleted());
        }
        SignedDocument signed = documents.signedDocument(issued.documentToken());
        consume(signed.compactSerialization());
    }

    /** Exercises the printer facade and its model. */
    public static void printers(EparagonyClient client) {
        Printers printers = client.printers();
        PrinterStatus status = printers.status(FiscalDeviceUniqueNumber.of("ZBN1901007833"));
        PrinterState state = status.state();
        consume(state.name());

        for (DailyReport report : printers.dailyReports(
                FiscalDeviceUniqueNumber.of("ZBN1901007833"), Instant.EPOCH, Instant.EPOCH)) {
            consume(report.issuedAt() + "/" + report.reportNumber()
                    + report.counters().hasAnomalies() + report.saleTotalIfReported());
        }
    }

    /** Exercises webhook verification, which a consumer reaches without holding API credentials. */
    public static void webhooks(byte[] rawBody, String signatureHeader) {
        WebhookVerifier verifier = EparagonyClient.webhookVerifier(WebhookSecret.of("secret"));
        verifier.verify(rawBody, signatureHeader);

        WebhookNotifications notifications =
                EparagonyClient.webhookNotifications(WebhookSecret.of("secret"));
        DocumentStatusNotification notification =
                notifications.documentStatus(rawBody, signatureHeader);
        consume(notification.status().state().name());

        ActionStatusNotification actionNotification =
                notifications.actionStatus(rawBody, signatureHeader);
        consume(actionNotification.action().state().name());
    }

    /** Exercises the exception hierarchy a consumer is expected to catch. */
    public static void errors(Runnable call) {
        try {
            call.run();
        } catch (EparagonyValidationException invalid) {
            invalid.errorCode().ifPresent(code -> consume(String.valueOf(code)));
        } catch (EparagonyRateLimitException throttled) {
            consume(String.valueOf(throttled.retryAfter()));
        } catch (EparagonyServerException serverFailure) {
            consume(String.valueOf(serverFailure.requestMayHaveBeenApplied()));
        } catch (EparagonyException other) {
            // Catches EparagonyAuthException and EparagonyAccessDeniedException too; they are subtypes,
            // and a probe that only has to compile does not need to distinguish them.
            consume(other.getMessage());
        }
    }

    /**
     * Sinks a value so the compiler cannot elide the call that produced it. This class exists to
     * compile, not to run; the parameter is consumed only to keep the reference live.
     */
    private static void consume(String value) {
        if (value != null && value.isEmpty()) {
            throw new IllegalStateException("unreachable; keeps the argument from being optimized away");
        }
    }
}
