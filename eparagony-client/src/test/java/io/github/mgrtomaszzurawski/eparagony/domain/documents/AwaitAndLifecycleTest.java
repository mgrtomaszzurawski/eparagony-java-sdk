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
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Waiting behaviour, scope gating and client lifecycle. */
class AwaitAndLifecycleTest {

    private static final String TOKEN_PATH = "/auth/token";
    private static final String DOCUMENT_TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final String STATUS_PATH = "/documents/" + DOCUMENT_TOKEN + "/status";

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
    @DisplayName("gives up waiting when the document never settles, saying so precisely")
    void timesOutOnNeverSettlingDocument() {
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"PENDING\"}")));

        EparagonyServerException failure = assertThrows(EparagonyServerException.class,
                () -> client().documents()
                        .awaitTerminalStatus(DocumentToken.of(DOCUMENT_TOKEN), Duration.ofSeconds(1)));

        assertTrue(failure.getMessage().contains("PENDING"),
                "the message must say what state it gave up on, but said: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("has not failed"),
                "a timeout is not a failed document and the message must not imply otherwise");
        // A timeout is not a write that may have landed; nothing was sent.
        assertTrue(!failure.requestMayHaveBeenApplied());
    }

    @Test
    @DisplayName("terminates rather than hanging when the clock does not advance")
    void terminatesWithAFrozenClock() {
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"PENDING\"}")));

        // The clock is injectable, so a caller can freeze it. Bounding the wait only by a deadline
        // would loop forever here — a hang being a far worse failure than a timeout.
        Clock frozen = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);

        assertThrows(EparagonyServerException.class,
                () -> clientWith(frozen).documents()
                        .awaitTerminalStatus(DocumentToken.of(DOCUMENT_TOKEN), Duration.ofSeconds(2)));
    }

    @Test
    @DisplayName("rejects a non-positive timeout instead of polling once and giving up")
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> client().documents()
                .awaitTerminalStatus(DocumentToken.of(DOCUMENT_TOKEN), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> client().documents()
                .awaitTerminalStatus(DocumentToken.of(DOCUMENT_TOKEN), Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("refuses a facade whose scope was never requested")
    void refusesFacadeWithoutScope() {
        // Without this the consumer would reach the endpoint and meet an opaque 403 — the exact
        // failure the scope guard exists to eliminate one layer up.
        EparagonyConfigurationException failure = assertThrows(EparagonyConfigurationException.class,
                () -> client().printers().status(io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber.of("ZBN1901007833")));

        assertTrue(failure.getMessage().contains(Scope.PRINTER_GET.wireValue()),
                "the message must name the missing scope, but said: " + failure.getMessage());
    }

    @Test
    @DisplayName("refuses use after close")
    void refusesUseAfterClose() {
        EparagonyClient client = client();
        client.close();

        assertThrows(IllegalStateException.class, client::documents);
        assertThrows(IllegalStateException.class, client::printers);
    }

    @Test
    @DisplayName("tolerates close being called twice")
    void closeIsIdempotent() {
        EparagonyClient client = client();

        client.close();
        client.close();

        assertThrows(IllegalStateException.class, client::documents);
    }

    private EparagonyClient client() {
        return clientWith(Clock.systemUTC());
    }

    private EparagonyClient clientWith(Clock clock) {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .build(), clock);
    }
}
