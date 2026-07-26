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

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a single issued document. A UUIDv4, either supplied by the caller or minted by the
 * server when the caller supplies none.
 *
 * <p>Do not assume it differs from the {@link TransactionToken}: when the caller supplies neither,
 * the server returns the same value for both. Do not assume it equals it either — the two are
 * independent concepts, and a retry after a fiscalization error deliberately reuses the transaction
 * token while taking a fresh document token.
 */
public record DocumentToken(String value) {

    public DocumentToken {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("documentToken must not be blank");
        }
    }

    public static DocumentToken of(String value) {
        return new DocumentToken(value);
    }

    /** Mints a fresh random token, for a caller that wants to know the identifier before issuing. */
    public static DocumentToken random() {
        return new DocumentToken(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
