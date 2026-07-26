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
package io.github.mgrtomaszzurawski.eparagony.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The token lifecycle: expiry-driven refresh, and the single re-authentication a 401 triggers.
 *
 * <p>Neither path was covered before, which meant an SDK that cached its token forever, or one that
 * re-authenticated in a loop, would both have left the suite green.
 */
class ReauthenticationAndExpiryTest {

    private static final String TOKEN_PATH = "/auth/token";
    private static final String DOCUMENT_TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final String STATUS_PATH = "/documents/" + DOCUMENT_TOKEN + "/status";

    private static final Instant START = Instant.parse("2026-07-26T00:00:00Z");
    private static final int TOKEN_LIFETIME_SECONDS = 3600;

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("re-authenticates exactly once when the API answers 401, then succeeds")
    void reauthenticatesOnceOn401() {
        stubToken("first-token");
        String scenario = "expired-token";
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401).withBody("{\"statusCode\":401}"))
                .willSetStateTo("authenticated"));
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).inScenario(scenario)
                .whenScenarioStateIs("authenticated")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"CONFIRMED\"}")));

        assertEquals("CONFIRMED",
                client(Clock.fixed(START, ZoneOffset.UTC))
                        .documents().status(DocumentToken.of(DOCUMENT_TOKEN)).state().name());

        // Two token requests: the initial one, and the one the 401 forced.
        server.verify(2, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
        server.verify(2, getRequestedFor(urlPathEqualTo(STATUS_PATH)));
    }

    @Test
    @DisplayName("gives up after one re-authentication rather than hammering the token endpoint")
    void doesNotLoopOnPersistent401() {
        stubToken("first-token");
        server.stubFor(get(urlPathEqualTo(STATUS_PATH))
                .willReturn(aResponse().withStatus(401).withBody("{\"statusCode\":401}")));

        Documents documents = client(Clock.fixed(START, ZoneOffset.UTC)).documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        assertThrows(EparagonyAuthException.class, () -> documents.status(token));

        // Exactly one retry, so exactly two of each. A loop here is what the server throttles.
        server.verify(2, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
        server.verify(2, getRequestedFor(urlPathEqualTo(STATUS_PATH)));
    }

    @Test
    @DisplayName("mints a fresh token once the cached one nears expiry")
    void refreshesNearExpiry() {
        stubToken("first-token");
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"CONFIRMED\"}")));

        MutableClock clock = new MutableClock(START);
        EparagonyClient client = client(clock);

        client.documents().status(DocumentToken.of(DOCUMENT_TOKEN));
        // Comfortably inside the lifetime: the cached token must be reused.
        clock.advance(Duration.ofMinutes(30));
        client.documents().status(DocumentToken.of(DOCUMENT_TOKEN));
        server.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));

        // Past the 60-second safety margin before expiry: a fresh token is required.
        clock.advance(Duration.ofSeconds(TOKEN_LIFETIME_SECONDS - 1800));
        client.documents().status(DocumentToken.of(DOCUMENT_TOKEN));
        server.verify(2, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    }

    @Test
    @DisplayName("attaches the refreshed token, not the stale one, after re-authenticating")
    void attachesRefreshedToken() {
        String scenario = "rotating-token";
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(tokenResponse("stale-token"))
                .willSetStateTo("rotated"));
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).inScenario(scenario)
                .whenScenarioStateIs("rotated")
                .willReturn(tokenResponse("fresh-token")));

        String statusScenario = "status-after-rotation";
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).inScenario(statusScenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401).withBody("{\"statusCode\":401}"))
                .willSetStateTo("accepted"));
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).inScenario(statusScenario)
                .whenScenarioStateIs("accepted")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"CONFIRMED\"}")));

        client(Clock.fixed(START, ZoneOffset.UTC)).documents().status(DocumentToken.of(DOCUMENT_TOKEN));

        server.verify(1, getRequestedFor(urlPathEqualTo(STATUS_PATH))
                .withHeader("Authorization", equalTo("Bearer stale-token")));
        server.verify(1, getRequestedFor(urlPathEqualTo(STATUS_PATH))
                .withHeader("Authorization", equalTo("Bearer fresh-token")));
    }

    private void stubToken(String value) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(tokenResponse(value)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder tokenResponse(
            String value) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"" + value + "\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":" + TOKEN_LIFETIME_SECONDS + ",\"scope\":\"document_create\"}");
    }

    private EparagonyClient client(Clock clock) {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .build(), clock);
    }

    /** A clock the test advances by hand, so token expiry is exercised without waiting an hour. */
    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant start) {
            this.current = start;
        }

        private void advance(Duration amount) {
            current = current.plus(amount);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
