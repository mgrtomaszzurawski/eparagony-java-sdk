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
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyNotFoundException;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ActionState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ActionType;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentAction;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.SignedDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

/**
 * Contract tests for the two document endpoints whose scopes the sandbox client is not granted.
 *
 * <p>These are WireMock-only and will stay that way until {@code document_action_get} and
 * {@code document_get_jws} are granted. That is recorded in {@code docs/TESTING.md} rather than being
 * quietly counted as coverage.
 */
class ActionsAndSignedDocumentTest {

    private static final String TOKEN_PATH = "/auth/token";
    private static final String DOCUMENT_TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final String ACTIONS_PATH = "/documents/" + DOCUMENT_TOKEN + "/actions/status";
    private static final String JWS_PATH = "/documents/" + DOCUMENT_TOKEN + "/jws";

    private static final String JWS_VALUE =
            "eyJhbGciOiJSUzI1NiJ9.eyJkb2N1bWVudCI6InJlY2VpcHQifQ.c2lnbmF0dXJl";

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
                        + "\"scope\":\"document_create document_action_get document_get_jws\"}")));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("parses every action attached to a document")
    void parsesActions() {
        server.stubFor(get(urlPathEqualTo(ACTIONS_PATH)).willReturn(jsonBody("""
                {"actions":[
                  {"actionId":"a-1","type":"DELIVER_VIA_ALLEGRO","status":"COMPLETED"},
                  {"actionId":"a-2","type":"DELIVER_VIA_ALLEGRO","status":"PENDING"},
                  {"actionId":"a-3","type":"DELIVER_VIA_ALLEGRO","status":"FAILED"}]}
                """)));

        List<DocumentAction> actions = documents().actions(DocumentToken.of(DOCUMENT_TOKEN));

        assertEquals(3, actions.size());
        assertEquals(ActionType.DELIVER_VIA_ALLEGRO, actions.get(0).type());
        assertTrue(actions.get(0).isCompleted());
        // PENDING is endpoint-only; the webhook never sends it, and it is not terminal.
        assertEquals(ActionState.PENDING, actions.get(1).state());
        assertTrue(!actions.get(1).state().isTerminal());
        assertTrue(actions.get(2).state().isTerminal());
    }

    @Test
    @DisplayName("returns an empty list for a document with no actions")
    void parsesEmptyActionList() {
        server.stubFor(get(urlPathEqualTo(ACTIONS_PATH)).willReturn(jsonBody("{\"actions\":[]}")));

        assertTrue(documents().actions(DocumentToken.of(DOCUMENT_TOKEN)).isEmpty());
    }

    @Test
    @DisplayName("keeps an unfamiliar action type and state rather than dropping the action")
    void toleratesUnfamiliarActionValues() {
        server.stubFor(get(urlPathEqualTo(ACTIONS_PATH)).willReturn(jsonBody(
                "{\"actions\":[{\"actionId\":\"a-9\",\"type\":\"DELIVER_VIA_SOMETHING\","
                        + "\"status\":\"HALF_DONE\"}]}")));

        List<DocumentAction> actions = documents().actions(DocumentToken.of(DOCUMENT_TOKEN));

        // The action still arrives: a fiscal notification is not worth dropping over an enum constant
        // this SDK predates.
        assertEquals(1, actions.size());
        assertEquals(ActionType.UNKNOWN, actions.get(0).type());
        assertEquals(ActionState.UNKNOWN, actions.get(0).state());
    }

    @Test
    @DisplayName("maps 404 for a document that does not exist")
    void mapsNotFoundOnActions() {
        server.stubFor(get(urlPathEqualTo(ACTIONS_PATH)).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"statusCode\":404,\"error\":\"Not Found\"}")));

        Documents documents = documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        assertThrows(EparagonyNotFoundException.class, () -> documents.actions(token));
    }

    @Test
    @DisplayName("returns the JWS verbatim without decoding it")
    void returnsJwsVerbatim() {
        server.stubFor(get(urlPathEqualTo(JWS_PATH))
                .willReturn(jsonBody("{\"JWS\":\"" + JWS_VALUE + "\"}")));

        SignedDocument signed = documents().signedDocument(DocumentToken.of(DOCUMENT_TOKEN));

        assertEquals(JWS_VALUE, signed.compactSerialization());
        server.verify(1, getRequestedFor(urlPathEqualTo(JWS_PATH))
                .withHeader("X-Api-Version", equalTo("3")));
    }

    @Test
    @DisplayName("refuses the actions call when its scope was never requested")
    void refusesActionsWithoutScope() {
        Documents documents = clientWith(Scope.DOCUMENT_CREATE).documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        EparagonyConfigurationException failure = assertThrows(EparagonyConfigurationException.class,
                () -> documents.actions(token));

        assertTrue(failure.getMessage().contains(Scope.DOCUMENT_ACTION_GET.wireValue()),
                "the message must name the missing scope, but said: " + failure.getMessage());
        // The point: no request is made, so no opaque 403 is produced.
        server.verify(0, getRequestedFor(urlPathEqualTo(ACTIONS_PATH)));
    }

    @Test
    @DisplayName("refuses the JWS call when its scope was never requested")
    void refusesJwsWithoutScope() {
        Documents documents = clientWith(Scope.DOCUMENT_CREATE).documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        EparagonyConfigurationException failure = assertThrows(EparagonyConfigurationException.class,
                () -> documents.signedDocument(token));

        assertTrue(failure.getMessage().contains(Scope.DOCUMENT_GET_JWS.wireValue()));
        server.verify(0, getRequestedFor(urlPathEqualTo(JWS_PATH)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder jsonBody(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private Documents documents() {
        return clientWith(Scope.DOCUMENT_CREATE, Scope.DOCUMENT_ACTION_GET, Scope.DOCUMENT_GET_JWS)
                .documents();
    }

    private EparagonyClient clientWith(Scope... scopes) {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .scopes(scopes)
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .build());
    }
}
