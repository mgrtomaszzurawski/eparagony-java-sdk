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
package io.github.mgrtomaszzurawski.eparagony.core.config;

/**
 * A deployment of the eparagony.pl API, as a pair of base URLs.
 *
 * <p>Two, not one, and this is the single most common way to misconfigure the integration.
 * Authentication does not live on the API host: tokens are minted on a separate {@code login.} host,
 * and {@code POST https://sandbox.eparagony.pl/auth/token} — the URL the published specification's
 * {@code tokenUrl} points at — answers {@code 404}. The specification is wrong on this point; the
 * values below are the ones verified against the live service.
 */
public enum Environment {

    /**
     * The test environment. Backed by a fiscal printer emulator, so it echoes request metadata
     * faithfully but returns constant device-side values (the same document number, the same
     * timestamp) for every document. See {@code KNOWN-SERVER-BEHAVIORS.md}.
     */
    SANDBOX("https://login.sandbox.eparagony.pl", "https://sandbox.eparagony.pl"),

    /** The production environment, backed by real registered cash registers. */
    PRODUCTION("https://login.eparagony.pl", "https://api.eparagony.pl");

    private final String authBaseUrl;
    private final String apiBaseUrl;

    Environment(String authBaseUrl, String apiBaseUrl) {
        this.authBaseUrl = authBaseUrl;
        this.apiBaseUrl = apiBaseUrl;
    }

    /** Base URL of the authorization server that mints access tokens. */
    public String authBaseUrl() {
        return authBaseUrl;
    }

    /** Base URL of the API that issues documents and reports status. */
    public String apiBaseUrl() {
        return apiBaseUrl;
    }
}
