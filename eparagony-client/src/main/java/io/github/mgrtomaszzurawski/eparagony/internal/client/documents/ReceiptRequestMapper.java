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
import java.util.Optional;

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
        Optional.ofNullable(request.documentToken())
                .ifPresent(token -> payload.documentToken(token.value()));
        Optional.ofNullable(request.transactionToken())
                .ifPresent(token -> payload.transactionToken(token.value()));
        Optional.ofNullable(request.statusUrl()).ifPresent(payload::statusUrl);
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
        Optional.ofNullable(request.orderId()).ifPresent(metadata::orderId);
        Optional.ofNullable(request.merchantDocumentId()).ifPresent(metadata::merchantDocumentId);
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
        Optional.ofNullable(line.unitOfMeasure()).ifPresent(product::unitOfMeasure);
        Optional.ofNullable(line.ean()).ifPresent(product::EAN);
        Optional.ofNullable(line.sku()).ifPresent(product::SKU);
        return new ReceiptLine(product);
    }

    private static Payment toPayment(ReceiptRequest request) {
        Payment payment = new Payment()
                .payments(request.payments().stream().map(ReceiptRequestMapper::toPaymentEntry).toList())
                .totalPaid(request.totalPaid().grosze());
        Optional.ofNullable(request.change()).ifPresent(change -> payment.change(change.grosze()));
        return payment;
    }

    private static Payments toPaymentEntry(PaymentEntry entry) {
        Payments payments = new Payments()
                .paymentForm(Payments.PaymentFormEnum.fromValue(entry.form().wireValue()))
                .paidThisForm(entry.amount().grosze());
        Optional.ofNullable(entry.name()).ifPresent(payments::paymentName);
        return payments;
    }
}
