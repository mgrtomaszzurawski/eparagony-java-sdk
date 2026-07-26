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
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAccessDeniedException;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterState;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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

/**
 * Contract tests for the printer status endpoint.
 *
 * <p>These exist because the live sandbox cannot provide them: its emulator reports a single fixed
 * state and never populates {@code lastActiveAt} or the nested {@code crkStatus}, so the parsing of
 * those fields can only be driven from a stub.
 */
class PrinterStatusTest {

    private static final String DEVICE = "ZBN1901007833";
    private static final String TOKEN_PATH = "/auth/token";
    private static final String STATUS_PATH = "/printers/" + DEVICE + "/status";

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
                        + "\"expires_in\":3600,\"scope\":\"printer_get\"}")));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("parses an active printer including both timestamps")
    void parsesActivePrinter() {
        stubStatus("""
                {"status":"ACTIVE",
                 "lastActiveAt":"2026-07-26T08:15:30Z",
                 "crkStatus":{"lastConnectedAt":"2026-07-25T23:59:00Z"}}
                """);

        PrinterStatus status = printers().status(FiscalDeviceUniqueNumber.of(DEVICE));

        assertEquals(PrinterState.ACTIVE, status.state());
        assertEquals(Instant.parse("2026-07-26T08:15:30Z"), status.lastActiveAtIfPresent().orElseThrow());
        assertEquals(Instant.parse("2026-07-25T23:59:00Z"),
                status.crkLastConnectedAtIfPresent().orElseThrow());
    }

    @Test
    @DisplayName("parses an inactive printer with null timestamps, as the emulator returns")
    void parsesInactivePrinterWithNullTimestamps() {
        // Verbatim what the live sandbox emulator answered on 2026-07-26.
        stubStatus("{\"status\":\"INACTIVE\",\"lastActiveAt\":null,\"crkStatus\":{\"lastConnectedAt\":null}}");

        PrinterStatus status = printers().status(FiscalDeviceUniqueNumber.of(DEVICE));

        assertEquals(PrinterState.INACTIVE, status.state());
        assertTrue(status.lastActiveAtIfPresent().isEmpty());
        assertTrue(status.crkLastConnectedAtIfPresent().isEmpty());
    }

    @Test
    @DisplayName("treats an absent crkStatus object as no CRK connection recorded")
    void toleratesAbsentCrkStatus() {
        stubStatus("{\"status\":\"ACTIVE\"}");

        PrinterStatus status = printers().status(FiscalDeviceUniqueNumber.of(DEVICE));

        assertEquals(PrinterState.ACTIVE, status.state());
        assertTrue(status.crkLastConnectedAtIfPresent().isEmpty());
    }

    @Test
    @DisplayName("maps an unfamiliar state to UNKNOWN rather than failing a monitoring call")
    void mapsUnfamiliarStateToUnknown() {
        stubStatus("{\"status\":\"SERVICE_MODE\"}");

        assertEquals(PrinterState.UNKNOWN, printers().status(FiscalDeviceUniqueNumber.of(DEVICE)).state());
    }

    @Test
    @DisplayName("ignores an unparseable timestamp instead of failing the whole call")
    void ignoresUnparseableTimestamp() {
        stubStatus("{\"status\":\"ACTIVE\",\"lastActiveAt\":\"26/07/2026 08:15\"}");

        PrinterStatus status = printers().status(FiscalDeviceUniqueNumber.of(DEVICE));

        assertEquals(PrinterState.ACTIVE, status.state());
        assertTrue(status.lastActiveAtIfPresent().isEmpty());
    }

    @Test
    @DisplayName("sends the required headers and the bearer token")
    void sendsRequiredHeaders() {
        stubStatus("{\"status\":\"ACTIVE\"}");

        printers().status(FiscalDeviceUniqueNumber.of(DEVICE));

        server.verify(1, getRequestedFor(urlPathEqualTo(STATUS_PATH))
                .withHeader("X-Api-Version", equalTo("3"))
                .withHeader("Authorization", equalTo("Bearer opaque")));
    }

    @Test
    @DisplayName("maps 403 on a device belonging to another client")
    void mapsForbidden() {
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"statusCode\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}")));

        Printers printers = printers();
        FiscalDeviceUniqueNumber device = FiscalDeviceUniqueNumber.of(DEVICE);

        assertThrows(EparagonyAccessDeniedException.class, () -> printers.status(device));
    }

    private void stubStatus(String body) {
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private Printers printers() {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .scopes(Scope.PRINTER_GET)
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .build()).printers();
    }
}
