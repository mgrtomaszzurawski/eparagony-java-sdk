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
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.AdditionalDescription;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ContentLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.AdvancePaymentSettlement;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.AllegroDelivery;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PackageDeposit;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptExtensions;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ProductCodes;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.RebateOrMarkup;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReturnPolicy;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.Warranty;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateTable;
import io.github.mgrtomaszzurawski.eparagony.rest.model.AdditionalDescriptionLine;
import io.github.mgrtomaszzurawski.eparagony.rest.model.Content;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ActionsInner;
import io.github.mgrtomaszzurawski.eparagony.rest.model.CurrencyExchange;
import io.github.mgrtomaszzurawski.eparagony.rest.model.DeliverViaAllegroAction;
import io.github.mgrtomaszzurawski.eparagony.rest.model.DutyFree;
import io.github.mgrtomaszzurawski.eparagony.rest.model.EReceiptCrkExtension;
import io.github.mgrtomaszzurawski.eparagony.rest.model.GiftCards;
import io.github.mgrtomaszzurawski.eparagony.rest.model.LoyaltyTransactionDetails;
import io.github.mgrtomaszzurawski.eparagony.rest.model.PackageReturn;
import io.github.mgrtomaszzurawski.eparagony.rest.model.SettlementAdvancePayment;
import io.github.mgrtomaszzurawski.eparagony.rest.model.KeyValueLine;
import io.github.mgrtomaszzurawski.eparagony.rest.model.LineRebate;
import io.github.mgrtomaszzurawski.eparagony.rest.model.LineWithBarcode;
import io.github.mgrtomaszzurawski.eparagony.rest.model.LineWithQRCode;
import io.github.mgrtomaszzurawski.eparagony.rest.model.SeparatorLine;
import io.github.mgrtomaszzurawski.eparagony.rest.model.TextLine;
import io.github.mgrtomaszzurawski.eparagony.rest.model.CreateReceiptDocumentPayload;
import io.github.mgrtomaszzurawski.eparagony.rest.model.RebatesMarkups;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ProductReturnPolicy;
import io.github.mgrtomaszzurawski.eparagony.rest.model.PDReceipt;
import io.github.mgrtomaszzurawski.eparagony.rest.model.Payment;
import io.github.mgrtomaszzurawski.eparagony.rest.model.Payments;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ReceiptLineProduct;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ReceiptMetadata;
import io.github.mgrtomaszzurawski.eparagony.rest.model.ReceiptMetadataAllOfConsumerTIN;
import io.github.mgrtomaszzurawski.eparagony.rest.model.TaxRates;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Maps the SDK's {@link ReceiptRequest} onto the generated Layer-1 payload. Package-private: the
 * {@code *Raw} types it touches must not escape into the domain.
 */
final class ReceiptRequestMapper {

    /** The only line kind this SDK issues so far; rebate lines are a separate {@code oneOf} branch. */
    private static final String LINE_TYPE_PRODUCT = "PRODUCT";

    /** The only action type the API publishes. */
    private static final String ACTION_DELIVER_VIA_ALLEGRO = "DELIVER_VIA_ALLEGRO";

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
        if (!request.actions().isEmpty()) {
            payload.actions(request.actions().stream().map(ReceiptRequestMapper::toAction).toList());
        }
        return payload;
    }

    private static PDReceipt toReceipt(ReceiptRequest request) {
        PDReceipt receipt = new PDReceipt()
                .fiscalize(request.fiscalize())
                .print(request.print())
                .metadata(toMetadata(request))
                .lines(toLines(request))
                .payment(toPayment(request));
        applyNonFiscalContent(receipt, request);
        return receipt;
    }

    /** Everything a receipt can carry beside its fiscal lines: deposits, advances, extras. */
    private static void applyNonFiscalContent(PDReceipt receipt, ReceiptRequest request) {
        if (!request.extensions().isEmpty()) {
            receipt.extensions(toExtensions(request.extensions()));
        }
        if (!request.packageReturns().isEmpty()) {
            receipt.packageReturns(request.packageReturns().stream()
                    .map(ReceiptRequestMapper::toPackageReturn).toList());
        }
        if (!request.returnPackagesIssued().isEmpty()) {
            receipt.returnPackagesIssued(request.returnPackagesIssued().stream()
                    .map(ReceiptRequestMapper::toPackageReturn).toList());
        }
        if (!request.settlementAdvancePayment().isEmpty()) {
            receipt.settlementAdvancePayment(request.settlementAdvancePayment().stream()
                    .map(ReceiptRequestMapper::toAdvanceSettlement).toList());
        }
        Optional.ofNullable(request.currencyExchange()).ifPresent(conversion ->
                receipt.currencyExchange(new CurrencyExchange()
                        .currency(conversion.currency())
                        .exchangeRate(conversion.exchangeRate().toPlainString())
                        .afterConversion(conversion.afterConversion().grosze())));
        Optional.ofNullable(request.dutyFree()).ifPresent(sale ->
                receipt.dutyFree(new DutyFree().destination(sale.destination()).stops(sale.stops())));
    }

    private static EReceiptCrkExtension toExtensions(ReceiptExtensions extensions) {
        EReceiptCrkExtension mapped = new EReceiptCrkExtension();
        if (!extensions.consumerLoyalty().isEmpty()) {
            mapped.consumerLoyalty(extensions.consumerLoyalty().stream()
                    .map(ReceiptRequestMapper::toLoyaltyMovement).toList());
        }
        if (!extensions.giftCards().isEmpty()) {
            mapped.giftCards(extensions.giftCards().stream()
                    .map(card -> new GiftCards()
                            .giftCardNo(card.giftCardNo())
                            .giftCardValue(card.giftCardValue().grosze()))
                    .toList());
        }
        extensions.recyclingDbIfPresent().ifPresent(mapped::recyclingDb);
        extensions.globalReturnPolicyIfPresent().ifPresent(policy ->
                mapped.globalReturnPolicy(toReturnPolicy(policy)));
        extensions.warrantyIfPresent().ifPresent(terms -> mapped.warranty(toWarranty(terms)));
        return mapped;
    }

    private static LoyaltyTransactionDetails toLoyaltyMovement(ReceiptExtensions.LoyaltyMovement move) {
        LoyaltyTransactionDetails mapped = new LoyaltyTransactionDetails().id(move.id());
        Optional.ofNullable(move.name()).ifPresent(mapped::name);
        // The spec types point counts as strings, not numbers. Kept typed in the domain and rendered
        // here, rather than pushing the API's choice onto the caller.
        Optional.ofNullable(move.pointsAdded()).ifPresent(points ->
                mapped.pointsAdded(points.toPlainString()));
        Optional.ofNullable(move.newBalance()).ifPresent(balance ->
                mapped.newBalance(balance.toPlainString()));
        Optional.ofNullable(move.additionalContent()).ifPresent(content ->
                mapped.additionalContent(toAdditionalContent(content)));
        return mapped;
    }

    private static Content toAdditionalContent(AdditionalDescription line) {
        return toAdditionalDescriptionLine(line);
    }

    private static PackageReturn toPackageReturn(PackageDeposit deposit) {
        PackageReturn mapped = new PackageReturn()
                .quantity(deposit.quantity())
                .unitPrice(deposit.unitPrice().grosze())
                .totalLineValue(deposit.totalLineValue().grosze());
        mapped.packageNumber(deposit.packageNumber());
        deposit.nameIfPresent().ifPresent(mapped::name);
        Optional.ofNullable(deposit.codes().ean()).ifPresent(mapped::EAN);
        Optional.ofNullable(deposit.codes().sku()).ifPresent(mapped::SKU);
        Optional.ofNullable(deposit.codes().plu()).ifPresent(mapped::PLU);
        Optional.ofNullable(deposit.codes().cn()).ifPresent(mapped::CN);
        Optional.ofNullable(deposit.codes().dataMatrix()).ifPresent(mapped::dataMatrix);
        Optional.ofNullable(deposit.codes().externalId()).ifPresent(mapped::externalId);
        return mapped;
    }

    private static SettlementAdvancePayment toAdvanceSettlement(AdvancePaymentSettlement settlement) {
        SettlementAdvancePayment mapped = new SettlementAdvancePayment()
                .nameOfPayment(settlement.nameOfPayment())
                .value(settlement.value().grosze())
                .taxRate(SettlementAdvancePayment.TaxRateEnum.fromValue(settlement.taxRate().name()));
        settlement.requiredAdditionalPaymentIfPresent().ifPresent(outstanding ->
                mapped.requiredAdditionalPayment(outstanding.grosze()));
        settlement.stornoIfPresent().ifPresent(mapped::isStorno);
        return mapped;
    }

    private static ActionsInner toAction(AllegroDelivery delivery) {
        DeliverViaAllegroAction action = new DeliverViaAllegroAction()
                .type(ACTION_DELIVER_VIA_ALLEGRO)
                .orderId(delivery.orderId());
        delivery.actionIdIfPresent().ifPresent(action::actionId);
        delivery.accountIdIfPresent().ifPresent(action::accountId);
        delivery.accountNameIfPresent().ifPresent(action::accountName);
        delivery.actionStatusUrlIfPresent().ifPresent(url -> action.actionStatusUrl(URI.create(url)));
        return new ActionsInner(action);
    }

    private static ReceiptMetadata toMetadata(ReceiptRequest request) {
        ReceiptMetadata metadata = new ReceiptMetadata()
                .grossSaleValue(request.grossSaleValue().grosze())
                .taxRates(toTaxRates(request.taxRates()));
        Optional.ofNullable(request.orderId()).ifPresent(metadata::orderId);
        Optional.ofNullable(request.merchantDocumentId()).ifPresent(metadata::merchantDocumentId);
        applyContext(metadata, request.metadata());
        return metadata;
    }

    private static void applyContext(ReceiptMetadata metadata,
            io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptMetadata context) {
        if (context == null || context.isEmpty()) {
            return;
        }
        Optional.ofNullable(context.cashRegisterId()).ifPresent(metadata::cashRegisterId);
        Optional.ofNullable(context.cashierId()).ifPresent(metadata::cashierId);
        Optional.ofNullable(context.shiftId()).ifPresent(metadata::shiftId);
        Optional.ofNullable(context.orderTime()).ifPresent(time -> metadata.orderTime(time.toString()));
        Optional.ofNullable(context.currency()).ifPresent(metadata::currency);
        // The spec models consumerTIN as a oneOf wrapper; its string branch is the one a receipt uses.
        Optional.ofNullable(context.consumerTIN()).ifPresent(tin ->
                metadata.consumerTIN(new ReceiptMetadataAllOfConsumerTIN(tin)));
        if (!context.additionalDescription().isEmpty()) {
            metadata.additionalDescription(context.additionalDescription().stream()
                    .map(ReceiptRequestMapper::toDescriptionLine).toList());
        }
    }

    /**
     * Wraps a document content line into the {@code oneOf} the metadata block expects.
     *
     * <p>Dispatched on the discriminator rather than on a Java enum, because the barcode branch has no
     * single discriminator of its own — the symbology IS the type, so nineteen distinct values all map
     * to the same generated shape.
     */
    private static AdditionalDescriptionLine toDescriptionLine(ContentLine line) {
        if (ContentLine.TYPE_TEXT.equals(line.type())) {
            return new AdditionalDescriptionLine(new TextLine().type(line.type()).body(line.body()));
        }
        if (ContentLine.TYPE_QR.equals(line.type())) {
            return new AdditionalDescriptionLine(
                    new LineWithQRCode().type(line.type()).body(line.body()));
        }
        if (ContentLine.TYPE_SEPARATOR.equals(line.type())) {
            return new AdditionalDescriptionLine(new SeparatorLine().type(line.type()));
        }
        if (ContentLine.TYPE_KEY_VALUE.equals(line.type())) {
            return new AdditionalDescriptionLine(new KeyValueLine()
                    .type(line.type()).key(line.key()).value(line.value()));
        }
        return new AdditionalDescriptionLine(new LineWithBarcode()
                .type(LineWithBarcode.TypeEnum.fromValue(line.type()))
                .body(line.body()));
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
        return request.lines().stream().map(ReceiptRequestMapper::toLineItem).toList();
    }

    /** Dispatches over the line list's two-way choice: a sold item, or a standalone discount. */
    private static ReceiptLine toLineItem(
            io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLineItem item) {
        if (item instanceof io.github.mgrtomaszzurawski.eparagony.domain.documents.model
                .ReceiptRebateLine rebateLine) {
            LineRebate mapped = new LineRebate()
                    .type(io.github.mgrtomaszzurawski.eparagony.domain.documents.model
                            .ReceiptRebateLine.LINE_TYPE)
                    .value(rebateLine.value().grosze());
            rebateLine.nameIfPresent().ifPresent(mapped::name);
            rebateLine.taxRateIfPresent()
                    .map(slot -> LineRebate.TaxRateEnum.fromValue(slot.name()))
                    .ifPresent(mapped::taxRate);
            return new ReceiptLine(mapped);
        }
        // Named rather than blind-cast. Java 17 has no exhaustive switch over a sealed type without
        // preview, so adding a third permitted subtype would compile here and fail at runtime with a
        // ClassCastException from inside a mapper. This turns that into a statement of what is missing.
        if (item instanceof io.github.mgrtomaszzurawski.eparagony.domain.documents.model
                .ReceiptLine line) {
            return toLine(line);
        }
        throw new IllegalStateException("unmapped receipt line branch: "
                + item.getClass().getName() + "; ReceiptRequestMapper must handle every "
                + "ReceiptLineItem the sealed interface permits");
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
        applyCodes(product, line.codes());
        Optional.ofNullable(line.storno()).ifPresent(product::isStorno);
        Optional.ofNullable(line.ticketRelief()).ifPresent(product::ticketRelief);
        Optional.ofNullable(line.returnPolicy()).ifPresent(policy ->
                product.returnPolicy(toReturnPolicy(policy)));
        Optional.ofNullable(line.warranty()).ifPresent(terms -> product.warranty(toWarranty(terms)));
        if (!line.rebatesMarkups().isEmpty()) {
            product.rebatesMarkups(line.rebatesMarkups().stream()
                    .map(ReceiptRequestMapper::toRebate).toList());
        }
        if (!line.additionalDescription().isEmpty()) {
            product.additionalDescription(line.additionalDescription().stream()
                    .map(ReceiptRequestMapper::toAdditionalDescriptionLine).toList());
        }
        return new ReceiptLine(product);
    }

    private static void applyCodes(ReceiptLineProduct product, ProductCodes codes) {
        Optional.ofNullable(codes.ean()).ifPresent(product::EAN);
        Optional.ofNullable(codes.sku()).ifPresent(product::SKU);
        Optional.ofNullable(codes.plu()).ifPresent(product::PLU);
        Optional.ofNullable(codes.pkwiu()).ifPresent(product::PKWIU);
        Optional.ofNullable(codes.cn()).ifPresent(product::CN);
        Optional.ofNullable(codes.dataMatrix()).ifPresent(product::dataMatrix);
        Optional.ofNullable(codes.externalId()).ifPresent(product::externalId);
    }

    private static ProductReturnPolicy toReturnPolicy(ReturnPolicy policy) {
        ProductReturnPolicy mapped = new ProductReturnPolicy();
        policy.productReturnDaysIfStated().ifPresent(mapped::productReturnDays);
        policy.inStoreReturnsOfferedIfStated().ifPresent(mapped::inStoreReturnsOffered);
        policy.additionalDescriptionIfStated().ifPresent(mapped::additionalDescription);
        return mapped;
    }

    private static io.github.mgrtomaszzurawski.eparagony.rest.model.Warranty toWarranty(
            Warranty terms) {
        io.github.mgrtomaszzurawski.eparagony.rest.model.Warranty mapped =
                new io.github.mgrtomaszzurawski.eparagony.rest.model.Warranty();
        terms.periodIfStated().ifPresent(mapped::period);
        Optional.ofNullable(terms.periodUnit()).ifPresent(unit -> mapped.periodUnit(
                io.github.mgrtomaszzurawski.eparagony.rest.model.Warranty.PeriodUnitEnum
                        .fromValue(unit.wireValue())));
        terms.dateToIfStated().ifPresent(instant -> mapped.dateTo(instant.toString()));
        terms.additionalDescriptionIfStated().ifPresent(mapped::additionalDescription);
        return mapped;
    }

    private static RebatesMarkups toRebate(RebateOrMarkup rebate) {
        return new RebatesMarkups().name(rebate.name()).value(rebate.value().grosze());
    }

    private static Content toAdditionalDescriptionLine(AdditionalDescription line) {
        Content mapped = new Content();
        line.text().ifPresent(mapped::textLine);
        Optional.ofNullable(line.graphicType()).ifPresent(mapped::graphicType);
        line.graphic().ifPresent(mapped::graphicLine);
        return mapped;
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
        Optional.ofNullable(entry.giftCardNo()).ifPresent(payments::giftCardNo);
        Optional.ofNullable(entry.giftCardValue()).ifPresent(value ->
                payments.giftCardValue(value.grosze()));
        Optional.ofNullable(entry.loyaltyCardNo()).ifPresent(payments::loyaltyCardNo);
        Optional.ofNullable(entry.currency()).ifPresent(payments::currency);
        Optional.ofNullable(entry.exchangeRate()).ifPresent(rate ->
                payments.exchangeRate(rate.toPlainString()));
        Optional.ofNullable(entry.cashInOriginalValue()).ifPresent(value ->
                payments.cashInOriginalValue(value.grosze()));
        return payments;
    }
}
