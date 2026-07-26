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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Substitutes {@code {placeholder}} segments in an {@link ApiPaths} template, URL-encoding each
 * value. Internal: never exported.
 */
public final class PathTemplate {

    private static final String PLUS = "+";
    private static final String ENCODED_SPACE = "%20";
    private static final String PARENT_SEGMENT = "..";
    private static final String CURRENT_SEGMENT = ".";

    private PathTemplate() {
    }

    /**
     * Renders {@code template} with {@code value} substituted for {@code {name}}.
     *
     * <p>Rejects path-traversal and empty segments outright rather than encoding them. {@code URLEncoder}
     * leaves {@code .} untouched, so a value of {@code ".."} survives encoding intact and would walk
     * the caller up a path segment — pointing a status lookup at an endpoint they did not intend.
     */
    public static String expand(String template, String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("path parameter '" + name + "' must not be blank");
        }
        String trimmed = value.trim();
        if (PARENT_SEGMENT.equals(trimmed) || CURRENT_SEGMENT.equals(trimmed)) {
            throw new IllegalArgumentException(
                    "path parameter '" + name + "' must not be a relative path segment");
        }
        return template.replace("{" + name + "}", encodeSegment(trimmed));
    }

    private static String encodeSegment(String value) {
        // URLEncoder targets form encoding, where a space becomes '+'. In a path segment '+' is a
        // literal plus, so it must be re-encoded.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace(PLUS, ENCODED_SPACE);
    }
}
