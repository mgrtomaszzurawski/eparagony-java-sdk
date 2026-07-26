package io.github.mgrtomaszzurawski.eparagony.jpms;

import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.config.Environment;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAccessDeniedException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;
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
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.IssuedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentForm;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateTable;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.Printers;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterState;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterStatus;

import java.time.Duration;

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
                .addPayment(PaymentEntry.of(PaymentForm.CARD, Amount.ofGrosze(100), "Visa"))
                .statusUrl("https://example.test/webhook")
                .build();

        Documents documents = client.documents();
        IssuedDocument issued = documents.issue(request, IdempotencyKey.random());
        DocumentStatus status = documents.awaitTerminalStatus(issued.documentToken(), Duration.ofMinutes(1));
        DocumentState state = status.state();
        status.documentUrlIfPresent().ifPresent(url -> consume(url + state));
        documents.status(DocumentToken.random());
    }

    /** Exercises the printer facade and its model. */
    public static void printers(EparagonyClient client) {
        Printers printers = client.printers();
        PrinterStatus status = printers.status(FiscalDeviceUniqueNumber.of("ZBN1901007833"));
        PrinterState state = status.state();
        consume(state.name());
    }

    /** Exercises webhook verification, which a consumer reaches without holding API credentials. */
    public static void webhooks(byte[] rawBody, String signatureHeader) {
        WebhookVerifier verifier = EparagonyClient.webhookVerifier(WebhookSecret.of("secret"));
        verifier.verify(rawBody, signatureHeader);
    }

    /** Exercises the exception hierarchy a consumer is expected to catch. */
    public static void errors(Runnable call) {
        try {
            call.run();
        } catch (EparagonyValidationException invalid) {
            invalid.errorCode().ifPresent(code -> consume(String.valueOf(code)));
        } catch (EparagonyAuthException | EparagonyAccessDeniedException denied) {
            consume(denied.getMessage());
        } catch (EparagonyServerException serverFailure) {
            consume(String.valueOf(serverFailure.requestMayHaveBeenApplied()));
        } catch (EparagonyException other) {
            consume(other.getMessage());
        }
    }

    private static void consume(String value) {
        // Deliberately empty: this class exists to compile, not to run.
    }
}
