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

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Reads optional fields out of a response tree. Internal: never exported.
 *
 * <p>One place, because the mappers previously each grew their own copy and the copies disagreed:
 * one treated a blank string as present, another as absent; one mapped a missing number to
 * {@code null} and another to zero. Those are decisions about what the server's silence means, and
 * the SDK should give the same answer everywhere.
 *
 * <p>The rules, stated once:
 * <ul>
 *   <li>absent, JSON {@code null}, or the wrong type ⇒ absent
 *   <li>a blank string ⇒ absent, because the API uses omission and emptiness interchangeably
 *   <li>an unparseable timestamp or number ⇒ absent rather than an exception; a fiscal confirmation
 *       is not worth losing to one malformed field
 * </ul>
 */
public final class JsonReader {

    private JsonReader() {
    }

    /** A text field, or {@code null} when absent, null, blank or not a string. */
    public static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    /** An integer field, or {@code null} when absent or not a number. */
    public static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    /**
     * An integer field, or {@code fallback} when absent. Used for counters, where the server omitting
     * a count means zero rather than unknown.
     */
    public static int integerOr(JsonNode node, String field, int fallback) {
        Integer value = integer(node, field);
        return value == null ? fallback : value;
    }

    /**
     * A boolean field. Empty when absent, which is not the same as {@code false} — the server not
     * saying whether paper was printed is not the server saying it was not.
     *
     * <p>Returns {@link Optional} rather than a nullable {@code Boolean}: a method whose declared type
     * is {@code Boolean} and which can hand back {@code null} is an auto-unboxing
     * {@code NullPointerException} waiting for its first caller.
     */
    public static Optional<Boolean> bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() ? Optional.of(value.asBoolean()) : Optional.empty();
    }

    /** An ISO-8601 instant, or {@code null} when absent or unparseable. */
    public static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException unparseable) {
            return null;
        }
    }

    /**
     * A decimal, accepted as either a JSON number or a decimal string. The fiscal reports send
     * amounts as strings; taking both keeps the mapper honest if that ever changes.
     */
    public static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText().trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
