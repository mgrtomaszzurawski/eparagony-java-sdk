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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.AdvancePaymentSettlement;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.AllegroDelivery;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ContentLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.CurrencyConversion;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DutyFreeSale;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PackageDeposit;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentForm;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.RebateOrMarkup;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRebateLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptExtensions;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptMetadata;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReturnPolicy;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.Warranty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Pins the wire form of a receipt that uses <em>every</em> optional structure the payload supports.
 *
 * <p>This test is what makes the "100% field depth" claim mean anything. The coverage tool measures
 * that a mapper <em>invokes</em> each Layer-1 setter; it cannot tell whether the resulting JSON is the
 * shape eparagony.pl expects. Only a pinned body can, and every placement below is one the
 * specification decides rather than the SDK: {@code isStorno} on the line and not the request,
 * {@code packageNumber} on a deposit, gift cards under {@code extensions} but gift-card <em>payments</em>
 * under {@code payment.payments}, five distinct shapes of printed content line, quantities as strings
 * and every amount as an integer count of grosze.
 */
class FullReceiptPayloadTest {

    private static final String POS_ID = "test-pos";
    private static final String TOKEN_PATH = "/auth/token";
    private static final String DOCUMENTS_PATH = "/documents";
    private static final String DOCUMENT_TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final String TRANSACTION_TOKEN = "99999999-8888-4777-8666-555555555555";

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":3600,\"scope\":\"document_create\"}")));
        server.stubFor(post(urlPathEqualTo(DOCUMENTS_PATH)).willReturn(aResponse()
                .withStatus(202)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"transactionToken":"%s","documentToken":"%s",
                         "documentPublicUrl":"https://hub.example/view/abc",
                         "documentStatusUrl":"https://api.example/documents/%s/status"}
                        """.formatted(TRANSACTION_TOKEN, DOCUMENT_TOKEN, DOCUMENT_TOKEN))));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("serializes every optional receipt structure into the shape the specification defines")
    void serializesTheFullPayload() {
        client().documents().issue(fullReceipt());

        server.verify(1, postRequestedFor(urlPathEqualTo(DOCUMENTS_PATH))
                .withRequestBody(equalToJson("""
                    {
                      "posId": "test-pos",
                      "documentToken": "11111111-2222-4333-8444-555555555555",
                      "transactionToken": "99999999-8888-4777-8666-555555555555",
                      "statusUrl": "https://shop.example/webhooks/eparagony",
                      "actions": [
                        {
                          "type": "DELIVER_VIA_ALLEGRO",
                          "orderId": "ALLEGRO-77",
                          "accountId": "acc-1",
                          "accountName": "MyShop",
                          "actionId": "ACT-1",
                          "actionStatusUrl": "https://shop.example/webhooks/allegro"
                        }
                      ],
                      "eReceipt": {
                        "fiscalize": true,
                        "print": true,
                        "metadata": {
                          "grossSaleValue": 9000,
                          "orderId": "ORDER-1183",
                          "merchantDocumentId": "DOC-1183",
                          "cashRegisterId": "TILL-2",
                          "cashierId": "CASHIER-7",
                          "shiftId": "SHIFT-A",
                          "orderTime": "2026-07-26T08:00:00Z",
                          "currency": "PLN",
                          "consumerTIN": "5252248481",
                          "taxRates": {"A":"23","B":"8","C":"5","D":"0","E":"ZW","F":"0","G":"0"},
                          "additionalDescription": [
                            {"type":"TEXT","body":"Dziekujemy za zakupy"},
                            {"type":"KEY_VALUE","key":"Zamowienie","value":"ORDER-1183"},
                            {"type":"EAN13","body":"590020137962"},
                            {"type":"QR","body":"https://shop.example/zwrot/1183"},
                            {"type":"SEPARATOR"}
                          ]
                        },
                        "lines": [
                          {
                            "type": "PRODUCT",
                            "productOrServiceName": "Karma sucha dla psa 1 kg",
                            "quantity": "2",
                            "unitOfMeasure": "szt.",
                            "unitPrice": 5000,
                            "totalLineValue": 10000,
                            "taxRate": "A",
                            "isStorno": false,
                            "ticketRelief": 0,
                            "EAN": "05902560100679",
                            "SKU": "SKU-1",
                            "PLU": "4036",
                            "PKWIU": "56.10.11.0",
                            "CN": "27102011D",
                            "dataMatrix": "DM-1",
                            "externalId": "EXT-1",
                            "rebatesMarkups": [{"name":"Rabat lojalnosciowy","value":-500},
                                               {"name":"Doplata za pakowanie","value":200}],
                            "additionalDescription": [{"textLine":"Produkt sezonowy"}],
                            "returnPolicy": {
                              "productReturnDays": 30,
                              "inStoreReturnsOffered": true,
                              "additionalDescription": "Zwrot w sklepie"
                            },
                            "warranty": {
                              "period": 24,
                              "periodUnit": "MONTH",
                              "additionalDescription": "Gwarancja producenta"
                            }
                          },
                          {
                            "type": "REBATE",
                            "value": -700,
                            "name": "Rabat dla stalych klientow",
                            "taxRate": "A"
                          }
                        ],
                        "payment": {
                          "payments": [
                            {
                              "paymentForm": "Karta",
                              "paymentName": "Visa",
                              "paidThisForm": 6000,
                              "loyaltyCardNo": "LOY-9",
                              "currency": "EUR",
                              "exchangeRate": "4.30",
                              "cashInOriginalValue": 1395
                            },
                            {
                              "paymentForm": "Voucher",
                              "paidThisForm": 4000,
                              "giftCardNo": "GC-1",
                              "giftCardValue": 4000
                            }
                          ],
                          "totalPaid": 10000,
                          "change": 700
                        },
                        "extensions": {
                          "recyclingDb": "BDO-12345",
                          "consumerLoyalty": [
                            {
                              "id": "LOY-9",
                              "name": "MyClub",
                              "pointsAdded": "-20.98",
                              "newBalance": "1234.56",
                              "additionalContent": {"graphicType":"QR","graphicLine":"https://shop.example/loyalty/9"}
                            }
                          ],
                          "giftCards": [{"giftCardNo":"GC-1","giftCardValue":4000}],
                          "globalReturnPolicy": {"productReturnDays": 14},
                          "warranty": {"dateTo": "2028-07-26T23:59:59.999+02:00"}
                        },
                        "settlementAdvancePayment": [
                          {
                            "nameOfPayment": "Zaliczka 2026/07/01",
                            "value": 2000,
                            "taxRate": "A",
                            "requiredAdditionalPayment": 7000,
                            "isStorno": false
                          }
                        ],
                        "packageReturns": [
                          {
                            "name": "Butelka zwrotna 0.5l",
                            "packageNumber": 3,
                            "quantity": 2,
                            "unitPrice": 100,
                            "totalLineValue": 200,
                            "EAN": "05902560100686",
                            "SKU": "SKU-BOTTLE",
                            "PLU": "9001",
                            "CN": "70109900",
                            "dataMatrix": "DM-BOTTLE",
                            "externalId": "EXT-BOTTLE"
                          }
                        ],
                        "returnPackagesIssued": [
                          {
                            "name": "Skrzynka",
                            "packageNumber": 7,
                            "quantity": 1,
                            "unitPrice": 500,
                            "totalLineValue": 500
                          }
                        ],
                        "currencyExchange": {
                          "currency": "EUR",
                          "exchangeRate": "4.30",
                          "afterConversion": 2093
                        },
                        "dutyFree": {
                          "destination": "Berlin",
                          "stops": ["Poznan", "Frankfurt (Oder)"]
                        }
                      }
                    }
                    """)));
    }

    private static ReceiptRequest fullReceipt() {
        return ReceiptRequest.builder()
                .documentToken(DocumentToken.of(DOCUMENT_TOKEN))
                .transactionToken(TransactionToken.of(TRANSACTION_TOKEN))
                .statusUrl("https://shop.example/webhooks/eparagony")
                .fiscalize(true)
                .print(true)
                .orderId("ORDER-1183")
                .merchantDocumentId("DOC-1183")
                .metadata(ReceiptMetadata.builder()
                        .cashRegisterId("TILL-2")
                        .cashierId("CASHIER-7")
                        .shiftId("SHIFT-A")
                        .orderTime(Instant.parse("2026-07-26T08:00:00Z"))
                        .currency("PLN")
                        .consumerTIN("5252248481")
                        .addAdditionalDescription(ContentLine.text("Dziekujemy za zakupy"))
                        .addAdditionalDescription(ContentLine.keyValue("Zamowienie", "ORDER-1183"))
                        .addAdditionalDescription(ContentLine.barcode(
                                ContentLine.BarcodeSymbology.EAN13, "590020137962"))
                        .addAdditionalDescription(ContentLine.qrCode("https://shop.example/zwrot/1183"))
                        .addAdditionalDescription(ContentLine.separator())
                        .build())
                .addLine(ReceiptLine.builder()
                        .productOrServiceName("Karma sucha dla psa 1 kg")
                        .quantity(2)
                        .unitOfMeasure("szt.")
                        .unitPrice(Amount.ofGrosze(5000))
                        .taxRate(TaxRateCode.A)
                        .storno(false)
                        .ticketRelief(0)
                        .ean("05902560100679")
                        .sku("SKU-1")
                        .plu("4036")
                        .pkwiu("56.10.11.0")
                        .cn("27102011D")
                        .dataMatrix("DM-1")
                        .externalId("EXT-1")
                        .addRebate(RebateOrMarkup.rebate("Rabat lojalnosciowy", Amount.ofGrosze(500)))
                        .addRebate(RebateOrMarkup.markup("Doplata za pakowanie", Amount.ofGrosze(200)))
                        .addAdditionalDescription(io.github.mgrtomaszzurawski.eparagony.domain.documents
                                .model.AdditionalDescription.ofText("Produkt sezonowy"))
                        .returnPolicy(ReturnPolicy.of(30, true, "Zwrot w sklepie"))
                        .warranty(Warranty.ofPeriod(24, Warranty.PeriodUnit.MONTH)
                                .describedAs("Gwarancja producenta"))
                        .build())
                .addRebateLine(ReceiptRebateLine.of("Rabat dla stalych klientow",
                        Amount.ofGrosze(700), TaxRateCode.A))
                .addPayment(PaymentEntry.builder(PaymentForm.CARD, Amount.ofGrosze(6000))
                        .name("Visa")
                        .loyaltyCardNo("LOY-9")
                        .foreignCurrency("EUR", new BigDecimal("4.30"), Amount.ofGrosze(1395))
                        .build())
                .addPayment(PaymentEntry.builder(PaymentForm.VOUCHER, Amount.ofGrosze(4000))
                        .giftCard("GC-1", Amount.ofGrosze(4000))
                        .build())
                // Deliberately not set: the builder derives it. Sale 9000, less a 200 deposit refunded
                // for returned bottles, plus a 500 deposit charged for the crate, is 9300 due against
                // 10000 tendered — so change must be 700, and the pinned body says 700.

                .extensions(ReceiptExtensions.builder()
                        .recyclingDb("BDO-12345")
                        .addLoyaltyMovement(ReceiptExtensions.LoyaltyMovement
                                .of("LOY-9", "MyClub", new BigDecimal("-20.98"), new BigDecimal("1234.56"))
                                .printing(io.github.mgrtomaszzurawski.eparagony.domain.documents.model
                                        .AdditionalDescription.ofGraphic(
                                                "QR", "https://shop.example/loyalty/9")))
                        .addGiftCard(new ReceiptExtensions.GiftCardUse("GC-1", Amount.ofGrosze(4000)))
                        .globalReturnPolicy(ReturnPolicy.ofDays(14))
                        .warranty(Warranty.until(OffsetDateTime.parse("2028-07-26T23:59:59.999+02:00")))
                        .build())
                .addAdvancePaymentSettlement(AdvancePaymentSettlement
                        .of("Zaliczka 2026/07/01", Amount.ofGrosze(2000), TaxRateCode.A)
                        // Advance 2000 against a 9000 sale leaves 7000. The server does not check this field —
                        // probed as informational — but a fixture that adds up to nothing reads as a typo.
                        .withOutstanding(Amount.ofGrosze(7000)))
                .addPackageReturn(PackageDeposit.of("Butelka zwrotna 0.5l", 3, 2, Amount.ofGrosze(100))
                        .identifiedBy(io.github.mgrtomaszzurawski.eparagony.domain.documents.model
                                .ProductCodes.builder().ean("05902560100686").sku("SKU-BOTTLE")
                                        .plu("9001").cn("70109900").dataMatrix("DM-BOTTLE")
                                        .externalId("EXT-BOTTLE").build()))
                .addReturnPackageIssued(PackageDeposit.of("Skrzynka", 7, 1, Amount.ofGrosze(500)))
                .currencyExchange(new CurrencyConversion("EUR", new BigDecimal("4.30"),
                        // Informational only, and the server does not check it — but a fixture that
                        // corresponds to nothing on the receipt reads as a typo. 9000 gr at 4.30.
                        Amount.ofGrosze(2093)))
                .dutyFree(DutyFreeSale.to("Berlin", List.of("Poznan", "Frankfurt (Oder)")))
                .addAction(AllegroDelivery.forOrder("ALLEGRO-77")
                        .identifiedAs("ACT-1")
                        .onAccount("acc-1", "MyShop")
                        .notifyingAt("https://shop.example/webhooks/allegro"))
                .build();
    }

    private EparagonyClient client() {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of(POS_ID))
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .build());
    }
}
