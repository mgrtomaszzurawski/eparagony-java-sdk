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
package io.github.mgrtomaszzurawski.eparagony.core.auth;

import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;

import java.util.Objects;

/**
 * A {@code client_id} / {@code client_secret} pair, issued per server by eparagony.pl through a
 * trusted channel. Sandbox and production credentials are distinct.
 *
 * <p>{@link #toString()} is overridden to redact the secret. Credential pairs end up in log lines
 * and debugger views by accident far more often than by intent, and a fiscal-integration secret in a
 * log file is a real incident.
 */
public record ClientCredentials(String clientId, String clientSecret) implements Credentials {

    private static final String GRANT_TYPE = "client_credentials";

    public ClientCredentials {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecret, "clientSecret");
        if (clientId.isBlank()) {
            throw new EparagonyConfigurationException("clientId must not be blank");
        }
        if (clientSecret.isBlank()) {
            throw new EparagonyConfigurationException("clientSecret must not be blank");
        }
    }

    @Override
    public String grantType() {
        return GRANT_TYPE;
    }

    @Override
    public String toString() {
        return "ClientCredentials[clientId=" + clientId + ", clientSecret=<redacted>]";
    }
}
