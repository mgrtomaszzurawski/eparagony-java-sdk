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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;
import org.openapitools.jackson.nullable.JsonNullableModule;

/**
 * The one JSON reader/writer in the SDK. Internal: never exported.
 *
 * <p>Unknown properties are ignored on purpose. The API's own integration guide states that the
 * contract is not rigid and that new fields may appear without warning, so a strict mapper would turn
 * a routine server-side addition into an outage for every deployed consumer.
 *
 * <p>Nulls are omitted on write. Most of the document payload is optional, and the API distinguishes
 * an absent field from a null one in places; sending explicit nulls for the dozens of fields a given
 * document does not use would be both noisy and, in the conditional-requirement cases, wrong.
 */
public final class JsonCodec {

    private final ObjectMapper objectMapper;

    public JsonCodec() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // Generated Layer-1 models express `nullable: true` as JsonNullable<T>; without this
                // module they serialize as a wrapper object and deserialize not at all.
                .registerModule(new JsonNullableModule())
                // NON_EMPTY, not NON_NULL. The generated Layer-1 models initialize every optional
                // collection to an empty list, so NON_NULL would put `"actions": []`,
                // `"rebatesMarkups": []` and four more onto the wire for a receipt that has none of
                // them — asserting things the caller never said. NON_EMPTY leaves numbers and
                // booleans alone (that is NON_DEFAULT's job), so `"print": false` and
                // `"unitPrice": 0` still serialize.
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Serializes a request body. */
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new EparagonyException("Failed to serialize a request body of type "
                    + value.getClass().getSimpleName(), failure);
        }
    }

    /** Deserializes a response body into {@code type}. */
    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException failure) {
            throw new EparagonyException("Failed to deserialize a response into "
                    + type.getSimpleName(), failure);
        }
    }

    /**
     * Parses a response into a tree. Used where the wire shape is a {@code oneOf} the SDK resolves
     * itself — the specification declares several of them without a usable discriminator, so the
     * concrete type is chosen from the payload rather than by Jackson.
     */
    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException failure) {
            throw new EparagonyException("Failed to parse a response as JSON", failure);
        }
    }

    /** Converts an already-parsed node into {@code type}. */
    public <T> T convert(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JsonProcessingException failure) {
            throw new EparagonyException("Failed to convert a JSON node into "
                    + type.getSimpleName(), failure);
        }
    }
}
