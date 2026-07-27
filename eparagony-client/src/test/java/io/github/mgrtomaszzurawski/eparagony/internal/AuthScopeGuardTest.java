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
import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the authorization behaviour that this API gets wrong in a way no other test would catch: a
 * token issued for a scope the server did not actually grant.
 */
class AuthScopeGuardTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String POS_ID = "test-pos";
    private static final String DOCUMENT_TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final String APPLICATION_USER_AGENT = "TestApp/1.0 (+https://example.test)";

    private static final String TOKEN_PATH = "/auth/token";
    private static final String STATUS_PATH = "/documents/" + DOCUMENT_TOKEN + "/status";

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
    @DisplayName("refuses a token the server issued without granting the requested scope")
    void refusesTokenWithoutGrantedScope() {
        // Exactly what the live server does for an unrecognised scope string: HTTP 200, a usable-looking
        // token, and no `scope` field at all. Every subsequent call would fail with a bare 403.
        stubTokenResponse("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\",\"expires_in\":3600}");

        Documents documents = client().documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        EparagonyAuthException failure = assertThrows(EparagonyAuthException.class,
                () -> documents.status(token));

        assertTrue(failure.getMessage().contains(Scope.DOCUMENT_CREATE.wireValue()),
                "the failure must name the missing scope, but said: " + failure.getMessage());
        // Pins the sentence, not just the scope name. This is the documented live behaviour — HTTP 200
        // with no `scope` field — so it is the message a reader actually meets, and a refactor once
        // garbled it into "the response granted carried no scope field at all" with every test green.
        assertTrue(failure.getMessage().contains("the response carried no scope field at all"),
                "the absent-scope case must read as a sentence, but said: " + failure.getMessage());
        // The point of the guard: fail at the token, never reaching the endpoint that would 403.
        server.verify(0, getRequestedFor(urlPathEqualTo(STATUS_PATH)));
    }

    @Test
    @DisplayName("refuses a token granting only some of the requested scopes")
    void refusesPartialGrant() {
        stubTokenResponse("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,\"scope\":\"document_create\"}");

        Documents documents = clientRequesting(Scope.DOCUMENT_CREATE, Scope.PRINTER_GET).documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        EparagonyAuthException failure = assertThrows(EparagonyAuthException.class,
                () -> documents.status(token));

        assertTrue(failure.getMessage().contains(Scope.PRINTER_GET.wireValue()),
                "the failure must name the ungranted scope, but said: " + failure.getMessage());
    }

    @Test
    @DisplayName("accepts a token whose granted scope covers the request")
    void acceptsGrantedToken() {
        stubTokenResponse("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,\"scope\":\"document_create\"}");
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"PENDING\",\"documentToken\":\"" + DOCUMENT_TOKEN + "\"}")));

        assertEquals("PENDING",
                client().documents().status(DocumentToken.of(DOCUMENT_TOKEN)).state().name());

        server.verify(1, getRequestedFor(urlPathEqualTo(STATUS_PATH))
                .withHeader("Authorization", equalTo("Bearer opaque")));
    }

    @Test
    @DisplayName("sends scopes space-separated, not comma-separated as the specification claims")
    void sendsSpaceSeparatedScopes() {
        stubTokenResponse("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,\"scope\":\"document_create printer_get\"}");
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"CONFIRMED\"}")));

        clientRequesting(Scope.DOCUMENT_CREATE, Scope.PRINTER_GET)
                .documents().status(DocumentToken.of(DOCUMENT_TOKEN));

        // In a form-encoded body a space is '+' (a comma would be '%2C'). This is the regression guard
        // for the separator, which the published field description gets wrong — and getting it wrong
        // does not fail loudly, it yields a 200 and a token that 403s everywhere.
        server.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withRequestBody(containing("scope=document_create+printer_get"))
                .withRequestBody(notMatching("(?s).*scope=[^&]*%2C.*")));
    }

    @Test
    @DisplayName("mints one token and reuses it across calls")
    void cachesTokenAcrossCalls() {
        stubTokenResponse("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,\"scope\":\"document_create\"}");
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"CONFIRMED\"}")));

        EparagonyClient client = client();
        client.documents().status(DocumentToken.of(DOCUMENT_TOKEN));
        client.documents().status(DocumentToken.of(DOCUMENT_TOKEN));
        client.documents().status(DocumentToken.of(DOCUMENT_TOKEN));

        // Re-minting per request is what the API throttles; three calls must cost one token.
        server.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_PATH)));
    }

    @Test
    @DisplayName("sends the required version and user-agent headers on the token call")
    void sendsRequiredHeadersWhenAuthenticating() {
        stubTokenResponse("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,\"scope\":\"document_create\"}");
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"CONFIRMED\"}")));

        client().documents().status(DocumentToken.of(DOCUMENT_TOKEN));

        server.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withHeader("X-Api-Version", equalTo("3"))
                .withHeader("User-Agent", containing("TestApp/1.0"))
                .withHeader("User-Agent", containing("eparagony-java-sdk/")));
    }

    private void stubTokenResponse(String body) {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private EparagonyClient client() {
        return clientRequesting(Scope.DOCUMENT_CREATE);
    }

    private EparagonyClient clientRequesting(Scope... scopes) {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials(CLIENT_ID, CLIENT_SECRET))
                .posId(PosId.of(POS_ID))
                .scopes(scopes)
                .applicationUserAgent(APPLICATION_USER_AGENT)
                .build());
    }
}
