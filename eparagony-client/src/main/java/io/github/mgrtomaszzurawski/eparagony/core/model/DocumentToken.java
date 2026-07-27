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

import io.github.mgrtomaszzurawski.eparagony.internal.ServerText;

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

    /** The shape the specification requires. Checked, because the Javadoc above promises it. */
    private static final java.util.regex.Pattern UUID_SHAPE = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public DocumentToken {
        Objects.requireNonNull(value, "value");
        if (!UUID_SHAPE.matcher(value).matches()) {
            // Sanitized here, not only where this is caught. The value comes off the wire, and the
            // translating catch upstream attaches THIS exception as the cause — so log.error(msg, ex)
            // would print an unbounded, CR/LF-bearing server string under "Caused by:", straight past
            // the one place that was supposed to bound it.
            throw new IllegalArgumentException(
                    "documentToken must be a UUID but was " + ServerText.quoted(value, "null"));
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
