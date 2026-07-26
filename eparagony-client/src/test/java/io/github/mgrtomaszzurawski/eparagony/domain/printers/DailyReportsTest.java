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
package io.github.mgrtomaszzurawski.eparagony.domain.printers;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.DailyReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract tests for daily fiscal reports. WireMock-only: {@code report_fiscal_get} is not granted. */
class DailyReportsTest {

    private static final String TOKEN_PATH = "/auth/token";
    private static final String DEVICE = "ZBN1901007833";
    private static final String REPORTS_PATH = "/printers/" + DEVICE + "/reports/daily";

    private static final String ONE_REPORT = """
            {"reports":[{
              "issuedAt":"2026-04-17T20:00:00Z",
              "saleFrom":"2026-04-17T00:00:00Z",
              "saleTo":"2026-04-17T19:00:00Z",
              "reportNumber":12273,
              "taxRates":{"A":"23","B":"8","C":"5","D":"0","E":"ZW","F":"0","G":"0"},
              "invoices":{"A":"10.04","B":"0"},
              "saleGross":{"A":"1230.50","C":"50.99"},
              "sale":{"A":"1000.41"},
              "tax":{"A":"230.09"},
              "saleTotal":"1281.49",
              "taxTotal":"230.09",
              "emergencySituationsCount":0,
              "canceledReceiptsCount":2,
              "nonFiscalDocumentsCount":1,
              "receiptsCount":41,
              "programmingEventsCount":0,
              "dbChangesCount":3,
              "nonFiscalErrorsCount":0,
              "communicationErrorsCount":5
            }]}
            """;

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
                        + "\"expires_in\":3600,\"scope\":\"report_fiscal_get\"}")));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("parses a report's amounts, rates and counters")
    void parsesReport() {
        server.stubFor(get(urlPathEqualTo(REPORTS_PATH)).willReturn(jsonBody(ONE_REPORT)));

        List<DailyReport> reports = printers(Scope.REPORT_FISCAL_GET)
                .dailyReports(FiscalDeviceUniqueNumber.of(DEVICE), null, null);

        assertEquals(1, reports.size());
        DailyReport report = reports.get(0);
        assertEquals(Instant.parse("2026-04-17T20:00:00Z"), report.issuedAt());
        assertEquals(12273, report.reportNumber());
        assertEquals("ZW", report.taxRates().get(TaxRateCode.E));
        // Amounts stay decimal złoty, exactly as the register reported them.
        assertEquals(new BigDecimal("1230.50"), report.saleGross().get(TaxRateCode.A));
        assertEquals(new BigDecimal("1281.49"), report.saleTotalIfReported().orElseThrow());
        assertEquals(41, report.counters().receipts());
        assertEquals(5, report.counters().communicationErrors());
        // Communication errors are exactly the thing worth alerting on.
        assertTrue(report.counters().hasAnomalies());
    }

    @Test
    @DisplayName("reports no anomalies when every error counter is zero")
    void reportsNoAnomaliesWhenClean() {
        server.stubFor(get(urlPathEqualTo(REPORTS_PATH))
                .willReturn(jsonBody(ONE_REPORT.replace("\"communicationErrorsCount\":5",
                        "\"communicationErrorsCount\":0"))));

        DailyReport report = printers(Scope.REPORT_FISCAL_GET)
                .dailyReports(FiscalDeviceUniqueNumber.of(DEVICE), null, null).get(0);

        assertTrue(!report.counters().hasAnomalies());
    }

    @Test
    @DisplayName("sends the date range as query parameters when given one")
    void sendsDateRange() {
        server.stubFor(get(urlPathEqualTo(REPORTS_PATH)).willReturn(jsonBody("{\"reports\":[]}")));

        printers(Scope.REPORT_FISCAL_GET).dailyReports(FiscalDeviceUniqueNumber.of(DEVICE),
                Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-04-30T23:59:59Z"));

        server.verify(1, getRequestedFor(urlPathEqualTo(REPORTS_PATH))
                .withQueryParam("issuedFrom", equalTo("2026-04-01T00:00:00Z"))
                .withQueryParam("issuedTo", equalTo("2026-04-30T23:59:59Z")));
    }

    @Test
    @DisplayName("omits the query parameters entirely when no range is given")
    void omitsAbsentDateRange() {
        server.stubFor(get(urlPathEqualTo(REPORTS_PATH)).willReturn(jsonBody("{\"reports\":[]}")));

        printers(Scope.REPORT_FISCAL_GET).dailyReports(FiscalDeviceUniqueNumber.of(DEVICE), null, null);

        server.verify(1, getRequestedFor(urlPathEqualTo(REPORTS_PATH))
                .withoutQueryParam("issuedFrom")
                .withoutQueryParam("issuedTo"));
    }

    @Test
    @DisplayName("rejects an inverted date range before spending a request on it")
    void rejectsInvertedRange() {
        Printers printers = printers(Scope.REPORT_FISCAL_GET);
        FiscalDeviceUniqueNumber device = FiscalDeviceUniqueNumber.of(DEVICE);
        Instant from = Instant.parse("2026-04-30T00:00:00Z");
        Instant to = Instant.parse("2026-04-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> printers.dailyReports(device, from, to));

        server.verify(0, getRequestedFor(urlPathEqualTo(REPORTS_PATH)));
    }

    @Test
    @DisplayName("drops an unparseable figure rather than losing the whole report")
    void toleratesMalformedFigure() {
        server.stubFor(get(urlPathEqualTo(REPORTS_PATH)).willReturn(jsonBody(
                ONE_REPORT.replace("\"saleTotal\":\"1281.49\"", "\"saleTotal\":\"n/a\""))));

        DailyReport report = printers(Scope.REPORT_FISCAL_GET)
                .dailyReports(FiscalDeviceUniqueNumber.of(DEVICE), null, null).get(0);

        assertTrue(report.saleTotalIfReported().isEmpty());
        // Everything else survived; one bad cell must not cost fifty reports.
        assertEquals(41, report.counters().receipts());
    }

    @Test
    @DisplayName("refuses the call when its scope was never requested")
    void refusesWithoutScope() {
        Printers printers = printers(Scope.PRINTER_GET);
        FiscalDeviceUniqueNumber device = FiscalDeviceUniqueNumber.of(DEVICE);

        EparagonyConfigurationException failure = assertThrows(EparagonyConfigurationException.class,
                () -> printers.dailyReports(device, null, null));

        assertTrue(failure.getMessage().contains(Scope.REPORT_FISCAL_GET.wireValue()));
        server.verify(0, getRequestedFor(urlPathEqualTo(REPORTS_PATH)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonBody(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private Printers printers(Scope... scopes) {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .scopes(scopes)
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .build()).printers();
    }
}
