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
package io.github.mgrtomaszzurawski.eparagony.core.model;

import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;

import java.util.Objects;

/**
 * Identifies the point of sale — a physical till or an e-commerce storefront — and is the identifier
 * every party to the transaction shares. Issued by eparagony.pl alongside the client credentials.
 *
 * <p>A distinct type rather than a {@code String} because it travels next to several other opaque
 * string identifiers, and transposing it with one of them produces a {@code 403} whose message names
 * none of them.
 */
public record PosId(String value) {

    public PosId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new EparagonyConfigurationException("posId must not be blank");
        }
    }

    /** Wraps the identifier issued by eparagony.pl, e.g. {@code "pos-10"}. */
    public static PosId of(String value) {
        return new PosId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
