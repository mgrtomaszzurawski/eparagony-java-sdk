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
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAccessDeniedException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyIdempotencyException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyNotFoundException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyValidationException;
import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.IdempotencyKey;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.core.retry.RetryPolicy;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.IssuedDocument;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentForm;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueReceiptTest {

    private static final String POS_ID = "test-pos";
    private static final String DOCUMENT_TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final String TOKEN_PATH = "/auth/token";
    private static final String DOCUMENTS_PATH = "/documents";
    private static final String STATUS_PATH = "/documents/" + DOCUMENT_TOKEN + "/status";

    private static final String CREATED_BODY = """
            {"transactionToken":"%s","documentToken":"%s",
             "documentPublicUrl":"https://hub.example/view/abc",
             "documentStatusUrl":"https://api.example/documents/%s/status"}
            """.formatted(DOCUMENT_TOKEN, DOCUMENT_TOKEN, DOCUMENT_TOKEN);

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
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("sends the receipt in the v3 shape, with amounts in grosze")
    void sendsV3Payload() {
        stubCreate(202);

        IssuedDocument issued = client().documents().issue(receipt());

        assertEquals(DOCUMENT_TOKEN, issued.documentToken().value());
        assertTrue(issued.fiscalizationPending(), "HTTP 202 means fiscalization is still under way");

        // The full request body is pinned, because every one of these placements is a v3 migration
        // trap: print/fiscalize belong INSIDE eReceipt (they were top-level in v1), the status
        // callback is `statusUrl` (renamed from `fiscalizationStatusUrl`), quantity is a string, and
        // every amount is an integer count of grosze.
        server.verify(1, postRequestedFor(urlPathEqualTo(DOCUMENTS_PATH))
                .withRequestBody(equalToJson("""
                        {
                          "posId": "test-pos",
                          "eReceipt": {
                            "fiscalize": true,
                            "print": false,
                            "metadata": {
                              "grossSaleValue": 10000,
                              "orderId": "ORDER-1",
                              "taxRates": {"A":"23","B":"8","C":"5","D":"0","E":"ZW","F":"0","G":"0"}
                            },
                            "lines": [
                              {
                                "type": "PRODUCT",
                                "productOrServiceName": "Karma sucha dla psa 1 kg",
                                "quantity": "1",
                                "unitPrice": 10000,
                                "totalLineValue": 10000,
                                "taxRate": "A",
                                "EAN": "05902560100679",
                                "isStorno": false
                              }
                            ],
                            "payment": {
                              "payments": [
                                {"paymentForm":"Karta","paymentName":"Visa","paidThisForm":10000}
                              ],
                              "totalPaid": 10000
                            }
                          }
                        }
                        """)));
    }

    @Test
    @DisplayName("sends the mandatory idempotency key and required headers")
    void sendsRequiredHeaders() {
        stubCreate(202);

        client().documents().issue(receipt());

        server.verify(1, postRequestedFor(urlPathEqualTo(DOCUMENTS_PATH))
                .withHeader("X-Api-Version", equalTo("3"))
                .withHeader("Content-Type", equalTo("application/json"))
                // A UUID, generated per call; without this header the API answers 422.
                .withHeader("Idempotency-Key",
                        matching("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    @Test
    @DisplayName("reuses a caller-supplied idempotency key verbatim")
    void reusesSuppliedIdempotencyKey() {
        stubCreate(202);
        IdempotencyKey key = IdempotencyKey.of("fixed-key-from-a-previous-attempt");

        client().documents().issue(receipt(), key);

        server.verify(1, postRequestedFor(urlPathEqualTo(DOCUMENTS_PATH))
                .withHeader("Idempotency-Key", equalTo(key.value())));
    }

    @Test
    @DisplayName("reports HTTP 200 as fiscalization not pending")
    void reportsNonPendingOn200() {
        stubCreate(200);

        assertFalse(client().documents().issue(receipt()).fiscalizationPending());
    }

    @Test
    @DisplayName("maps each failure status onto the exception whose remediation matches")
    void mapsErrorStatuses() {
        assertMapsTo(400, "{\"error\":\"Bad Request\",\"statusCode\":400,\"errorCode\":120}",
                EparagonyValidationException.class);
        assertMapsTo(403, "{\"statusCode\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}",
                EparagonyAccessDeniedException.class);
        assertMapsTo(404, "{\"statusCode\":404,\"error\":\"Not Found\"}",
                EparagonyNotFoundException.class);
        assertMapsTo(422, "{\"statusCode\":422,\"error\":\"Unprocessable Entity\"}",
                EparagonyIdempotencyException.class);
        assertMapsTo(500, "{\"statusCode\":500}", EparagonyServerException.class);
    }

    @Test
    @DisplayName("surfaces the server's numeric error code on a validation failure")
    void surfacesErrorCode() {
        server.stubFor(post(urlPathEqualTo(DOCUMENTS_PATH)).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Bad Request\",\"statusCode\":400,\"errorCode\":120}")));

        EparagonyValidationException failure = assertThrows(EparagonyValidationException.class,
                () -> client().documents().issue(receipt()));

        assertEquals(120, failure.errorCode().orElseThrow());
    }

    @Test
    @DisplayName("flags a write that failed after the request may already have been applied")
    void flagsPossiblyAppliedWrite() {
        server.stubFor(post(urlPathEqualTo(DOCUMENTS_PATH))
                .willReturn(aResponse().withStatus(500).withBody("{\"statusCode\":500}")));

        EparagonyServerException failure = assertThrows(EparagonyServerException.class,
                () -> client().documents().issue(receipt()));

        // The caller must not blindly reissue: the sale may already be fiscalized.
        assertTrue(failure.requestMayHaveBeenApplied());
    }

    @Test
    @DisplayName("does not retry a document write by default")
    void doesNotRetryWritesByDefault() {
        server.stubFor(post(urlPathEqualTo(DOCUMENTS_PATH))
                .willReturn(aResponse().withStatus(500).withBody("{\"statusCode\":500}")));

        assertThrows(EparagonyServerException.class, () -> client().documents().issue(receipt()));

        // One attempt, not three: reissuing a receipt is not safe without the caller's consent.
        server.verify(1, postRequestedFor(urlPathEqualTo(DOCUMENTS_PATH)));
    }

    @Test
    @DisplayName("keeps one idempotency key across the retries of a single call")
    void keepsOneKeyAcrossRetries() {
        server.stubFor(post(urlPathEqualTo(DOCUMENTS_PATH))
                .willReturn(aResponse().withStatus(500).withBody("{\"statusCode\":500}")));

        assertThrows(EparagonyServerException.class,
                () -> retryingClient().documents().issue(receipt()));

        // This is what makes opting into write retries safe at all: the server sees one repeated
        // request, not three distinct ones, so the sale is fiscalized at most once.
        var requests = server.findAll(postRequestedFor(urlPathEqualTo(DOCUMENTS_PATH)));
        assertEquals(3, requests.size(), "the retry policy asked for three attempts");
        assertEquals(1, requests.stream()
                        .map(request -> request.getHeader("Idempotency-Key"))
                        .distinct().count(),
                "all attempts of one call must carry the same Idempotency-Key");
    }

    @Test
    @DisplayName("polls until the document reaches a terminal state")
    void pollsUntilTerminal() {
        String scenario = "fiscalization";
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).inScenario(scenario)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(jsonBody("{\"status\":\"PENDING\"}"))
                .willSetStateTo("second"));
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).inScenario(scenario)
                .whenScenarioStateIs("second")
                .willReturn(jsonBody("""
                        {"status":"CONFIRMED","documentType":"RECEIPT","fiscalDocumentNumber":570,
                         "fiscalDeviceUniqueNumber":"ZBN1901007833","receiptNumber":210,
                         "endTime":"2022-07-20T11:26:38.596Z","printed":false}
                        """)));

        var status = client().documents()
                .awaitTerminalStatus(DocumentToken.of(DOCUMENT_TOKEN), java.time.Duration.ofSeconds(30));

        assertEquals(DocumentState.CONFIRMED, status.state());
        assertTrue(status.isConfirmed());
        assertEquals(570, status.fiscalDocumentNumberIfPresent().orElseThrow());
        assertEquals("ZBN1901007833", status.fiscalDeviceUniqueNumberIfPresent().orElseThrow().value());
    }

    @Test
    @DisplayName("stops waiting on a terminal failure rather than polling to the timeout")
    void stopsOnTerminalError() {
        server.stubFor(get(urlPathEqualTo(STATUS_PATH))
                .willReturn(jsonBody("{\"status\":\"ERROR\",\"errorMessage\":\"schodek podatkowy\"}")));

        var status = client().documents()
                .awaitTerminalStatus(DocumentToken.of(DOCUMENT_TOKEN), java.time.Duration.ofSeconds(30));

        assertEquals(DocumentState.ERROR, status.state());
        assertEquals("schodek podatkowy", status.errorMessageIfPresent().orElseThrow());
    }

    @Test
    @DisplayName("treats an unfamiliar status as non-terminal rather than failing")
    void toleratesUnknownStatus() {
        server.stubFor(get(urlPathEqualTo(STATUS_PATH))
                .willReturn(jsonBody("{\"status\":\"SOME_FUTURE_STATE\"}")));

        var status = client().documents().status(DocumentToken.of(DOCUMENT_TOKEN));

        assertEquals(DocumentState.UNKNOWN, status.state());
        assertFalse(status.isTerminal());
    }

    private void assertMapsTo(int statusCode, String body, Class<? extends RuntimeException> expected) {
        server.stubFor(post(urlPathEqualTo(DOCUMENTS_PATH)).willReturn(aResponse()
                .withStatus(statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));

        assertThrows(expected, () -> client().documents().issue(receipt()));
    }

    private void stubCreate(int statusCode) {
        server.stubFor(post(urlPathEqualTo(DOCUMENTS_PATH))
                .willReturn(aResponse().withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CREATED_BODY)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonBody(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static ReceiptRequest receipt() {
        return ReceiptRequest.builder()
                .orderId("ORDER-1")
                .addLine(ReceiptLine.builder()
                        .productOrServiceName("Karma sucha dla psa 1 kg")
                        .ean("05902560100679")
                        .quantity(1)
                        .unitPrice(Amount.ofGrosze(10000))
                        .taxRate(TaxRateCode.A)
                        .build())
                .addPayment(PaymentEntry.of(PaymentForm.CARD, Amount.ofGrosze(10000), "Visa"))
                .build();
    }

    private EparagonyClient client() {
        return clientWith(RetryPolicy.defaults());
    }

    private EparagonyClient retryingClient() {
        return clientWith(RetryPolicy.builder()
                .maxAttempts(3)
                .retryPost(true)
                .initialBackoff(java.time.Duration.ofMillis(1))
                .maxBackoff(java.time.Duration.ofMillis(2))
                .build());
    }

    private EparagonyClient clientWith(RetryPolicy retryPolicy) {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of(POS_ID))
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .retryPolicy(retryPolicy)
                .build());
    }
}
