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
 * The {@code Idempotency-Key} sent with a document-issuing request. Mandatory: the API answers
 * {@code 422} without one.
 *
 * <p>The SDK mints a fresh key per call by default. Supply your own only to <em>deliberately</em>
 * repeat a request — most importantly after a timeout, where the document may already have been
 * issued and resending under a new key would fiscalize the same sale twice. Reusing a key with a
 * changed payload is itself an error and answers {@code 422}.
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    /** Mints a fresh key. Used automatically for every call that does not carry an explicit one. */
    public static IdempotencyKey random() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
