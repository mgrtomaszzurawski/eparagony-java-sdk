package io.github.mgrtomaszzurawski.eparagony.internal.client.documents;

import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateTable;
import io.github.mgrtomaszzurawski.eparagony.rest.model.CreateReceiptDocumentPayload;
import io.github.mgrtomaszzurawski.eparagony.rest.model.PDReceipt;
import io.github.mgrtomaszzurawski.eparagony.rest.model.Payment;
import io.github.mgrtomaszzurawski.eparagony.rest.model.Payments;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ReceiptLineProduct;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ReceiptMetadata;
import io.github.mgrtomaszzurawski.eparagony.rest.model.TaxRates;

import java.util.List;

/**
 * Maps the SDK's {@link ReceiptRequest} onto the generated Layer-1 payload. Package-private: the
 * {@code *Raw} types it touches must not escape into the domain.
 */
final class ReceiptRequestMapper {

    /** The only line kind this SDK issues so far; rebate lines are a separate {@code oneOf} branch. */
    private static final String LINE_TYPE_PRODUCT = "PRODUCT";

    private ReceiptRequestMapper() {
    }

    static CreateReceiptDocumentPayload toPayload(ReceiptRequest request, PosId posId) {
        CreateReceiptDocumentPayload payload = new CreateReceiptDocumentPayload()
                .posId(posId.value())
                .eReceipt(toReceipt(request));
        request.documentTokenIfPresent().ifPresent(token -> payload.documentToken(token.value()));
        request.transactionTokenIfPresent().ifPresent(token -> payload.transactionToken(token.value()));
        request.statusUrlIfPresent().ifPresent(payload::statusUrl);
        return payload;
    }

    private static PDReceipt toReceipt(ReceiptRequest request) {
        return new PDReceipt()
                .fiscalize(request.fiscalize())
                .print(request.print())
                .metadata(toMetadata(request))
                .lines(toLines(request))
                .payment(toPayment(request));
    }

    private static ReceiptMetadata toMetadata(ReceiptRequest request) {
        ReceiptMetadata metadata = new ReceiptMetadata()
                .grossSaleValue(request.grossSaleValue().grosze())
                .taxRates(toTaxRates(request.taxRates()));
        request.orderIdIfPresent().ifPresent(metadata::orderId);
        request.merchantDocumentIdIfPresent().ifPresent(metadata::merchantDocumentId);
        return metadata;
    }

    private static TaxRates toTaxRates(TaxRateTable table) {
        return new TaxRates()
                .A(table.rateFor(TaxRateCode.A))
                .B(table.rateFor(TaxRateCode.B))
                .C(table.rateFor(TaxRateCode.C))
                .D(table.rateFor(TaxRateCode.D))
                .E(table.rateFor(TaxRateCode.E))
                .F(table.rateFor(TaxRateCode.F))
                .G(table.rateFor(TaxRateCode.G));
    }

    private static List<ReceiptLine> toLines(ReceiptRequest request) {
        return request.lines().stream().map(ReceiptRequestMapper::toLine).toList();
    }

    private static ReceiptLine toLine(io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine line) {
        ReceiptLineProduct product = new ReceiptLineProduct()
                .type(LINE_TYPE_PRODUCT)
                .productOrServiceName(line.productOrServiceName())
                // The wire type is a string, not a number: the register accepts fractional quantities
                // and the spec models them as text to avoid a float round-tripping through JSON.
                .quantity(line.quantity().toPlainString())
                .unitPrice(line.unitPrice().grosze())
                .totalLineValue(line.totalLineValue().grosze())
                .taxRate(ReceiptLineProduct.TaxRateEnum.fromValue(line.taxRate().name()));
        line.unitOfMeasureIfPresent().ifPresent(product::unitOfMeasure);
        line.eanIfPresent().ifPresent(product::EAN);
        line.skuIfPresent().ifPresent(product::SKU);
        return new ReceiptLine(product);
    }

    private static Payment toPayment(ReceiptRequest request) {
        Payment payment = new Payment()
                .payments(request.payments().stream().map(ReceiptRequestMapper::toPaymentEntry).toList())
                .totalPaid(request.totalPaid().grosze());
        request.changeIfPresent().ifPresent(change -> payment.change(change.grosze()));
        return payment;
    }

    private static Payments toPaymentEntry(PaymentEntry entry) {
        Payments payments = new Payments()
                .paymentForm(Payments.PaymentFormEnum.fromValue(entry.form().wireValue()))
                .paidThisForm(entry.amount().grosze());
        entry.nameIfPresent().ifPresent(payments::paymentName);
        return payments;
    }
}
