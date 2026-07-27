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
 * Identifies the commercial transaction a document belongs to. A UUIDv4.
 *
 * <p>It outlives any single document. When fiscalization fails — the classic case being the Polish
 * <em>schodek podatkowy</em>, a tax-rate ordering violation the cash register rejects — the
 * documented recovery is to reissue under the <em>same</em> transaction token with a fresh
 * {@link DocumentToken}. The new fiscal document is then served from the visualization URL minted by
 * the first attempt.
 */
public record TransactionToken(String value) {

    /** The shape the specification requires. Checked, because the Javadoc above promises it. */
    private static final java.util.regex.Pattern UUID_SHAPE = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public TransactionToken {
        Objects.requireNonNull(value, "value");
        if (!UUID_SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "transactionToken must be a UUID but was \"" + value + "\"");
        }
    }

    public static TransactionToken of(String value) {
        return new TransactionToken(value);
    }

    public static TransactionToken random() {
        return new TransactionToken(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
