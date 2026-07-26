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

import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.config.Environment;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathAndConfigTest {

    private static final String TEMPLATE = "/documents/{documentToken}/status";

    @Test
    @DisplayName("substitutes and encodes a path parameter")
    void expandsTemplate() {
        assertEquals("/documents/abc-123/status", PathTemplate.expand(TEMPLATE, "documentToken", "abc-123"));
        assertEquals("/documents/a%2Fb/status", PathTemplate.expand(TEMPLATE, "documentToken", "a/b"));
        assertEquals("/documents/a%20b/status", PathTemplate.expand(TEMPLATE, "documentToken", "a b"));
    }

    @Test
    @DisplayName("rejects a traversal segment instead of encoding it")
    void rejectsTraversal() {
        // URLEncoder leaves '.' untouched, so ".." survives encoding intact and would walk the caller
        // up a path segment onto an endpoint they did not ask for.
        assertThrows(IllegalArgumentException.class,
                () -> PathTemplate.expand(TEMPLATE, "documentToken", ".."));
        assertThrows(IllegalArgumentException.class,
                () -> PathTemplate.expand(TEMPLATE, "documentToken", "."));
        assertThrows(IllegalArgumentException.class,
                () -> PathTemplate.expand(TEMPLATE, "documentToken", "  "));
    }

    @Test
    @DisplayName("points authentication and API traffic at different hosts")
    void separatesAuthAndApiHosts() {
        // The single most common way to misconfigure this integration. The published tokenUrl claims
        // auth lives on the API host; it does not, and that URL answers 404.
        assertNotEquals(Environment.SANDBOX.authBaseUrl(), Environment.SANDBOX.apiBaseUrl());
        assertNotEquals(Environment.PRODUCTION.authBaseUrl(), Environment.PRODUCTION.apiBaseUrl());
        assertTrue(Environment.SANDBOX.authBaseUrl().startsWith("https://login."));
        assertTrue(Environment.PRODUCTION.authBaseUrl().startsWith("https://login."));
    }

    @Test
    @DisplayName("defaults to the sandbox so production is an explicit choice")
    void defaultsToSandbox() {
        assertEquals(Environment.SANDBOX, validConfig().build().environment());
    }

    @Test
    @DisplayName("rejects a generic user agent, which the API itself rejects")
    void rejectsGenericUserAgent() {
        assertThrows(EparagonyConfigurationException.class,
                () -> validConfig().applicationUserAgent("Java/17").build());
        assertThrows(EparagonyConfigurationException.class,
                () -> validConfig().applicationUserAgent("curl/8.5.0").build());
        assertThrows(EparagonyConfigurationException.class,
                () -> validConfig().applicationUserAgent("ok").build());
    }

    @Test
    @DisplayName("requires the credentials, posId and user agent up front")
    void requiresMandatoryFields() {
        assertThrows(EparagonyConfigurationException.class, () -> EparagonyConfig.builder()
                .posId(PosId.of("pos"))
                .applicationUserAgent("App/1.0")
                .build());
        assertThrows(EparagonyConfigurationException.class, () -> EparagonyConfig.builder()
                .credentials(new ClientCredentials("id", "secret"))
                .applicationUserAgent("App/1.0")
                .build());
        assertThrows(EparagonyConfigurationException.class, () -> EparagonyConfig.builder()
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .build());
    }

    @Test
    @DisplayName("keeps scope order stable so the token request is reproducible")
    void keepsScopeOrderStable() {
        String first = Scope.toWireValue(validConfig()
                .scopes(Scope.PRINTER_GET, Scope.DOCUMENT_CREATE).build().scopes());
        String second = Scope.toWireValue(validConfig()
                .scopes(Scope.DOCUMENT_CREATE, Scope.PRINTER_GET).build().scopes());

        assertEquals(first, second, "scope order must not depend on the order they were declared in");
        assertEquals("document_create printer_get", first);
    }

    @Test
    @DisplayName("keeps the client secret out of toString")
    void redactsClientSecret() {
        String rendered = new ClientCredentials("public-id", "super-secret-value").toString();

        assertFalse(rendered.contains("super-secret-value"));
        assertTrue(rendered.contains("public-id"), "the id is not a secret and aids debugging");
    }

    private static EparagonyConfig.Builder validConfig() {
        return EparagonyConfig.builder()
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .applicationUserAgent("TestApp/1.0 (+https://example.test)");
    }
}
