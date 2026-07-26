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
package io.github.mgrtomaszzurawski.eparagony.core.webhook;

import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;

import java.util.Objects;

/**
 * The shared secret eparagony.pl issues for signing webhook notifications. Distinct from the client
 * credentials and used for nothing else.
 *
 * <p>{@link #toString()} is redacted: this value is the only thing standing between a forged HTTP
 * request and your accounting.
 */
public record WebhookSecret(String value) {

    public WebhookSecret {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new EparagonyConfigurationException("webhookSecret must not be blank");
        }
    }

    public static WebhookSecret of(String value) {
        return new WebhookSecret(value);
    }

    @Override
    public String toString() {
        return "WebhookSecret[<redacted>]";
    }
}
