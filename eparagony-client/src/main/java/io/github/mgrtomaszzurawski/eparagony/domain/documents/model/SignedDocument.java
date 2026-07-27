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
package io.github.mgrtomaszzurawski.eparagony.domain.documents.model;

import java.util.Objects;

/**
 * An issued document in JWS form — the signed original, as opposed to the human-readable
 * visualization.
 *
 * <p>The SDK hands back the compact serialization verbatim and does not attempt to verify or decode
 * it. Verifying a JWS requires the issuer's key material and a policy about which keys to trust; both
 * are the consumer's to decide, and an SDK that guessed at them would be asserting a trust decision it
 * has no standing to make.
 *
 * @param compactSerialization the JWS in {@code header.payload.signature} form
 */
public record SignedDocument(String compactSerialization) {

    public SignedDocument {
        Objects.requireNonNull(compactSerialization, "compactSerialization");
        if (compactSerialization.isBlank()) {
            throw new IllegalArgumentException("a JWS must not be blank");
        }
    }
}
