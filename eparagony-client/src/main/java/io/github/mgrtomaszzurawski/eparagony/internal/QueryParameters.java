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
import java.util.Map;
import java.util.StringJoiner;

/** Renders a query string, URL-encoding names and values. Internal: never exported. */
public final class QueryParameters {

    private static final String QUERY_PREFIX = "?";
    private static final String PARAMETER_SEPARATOR = "&";
    private static final String VALUE_SEPARATOR = "=";

    private QueryParameters() {
    }

    /** Renders {@code parameters} as {@code ?a=1&b=2}, or an empty string when there are none. */
    public static String render(Map<String, String> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        StringJoiner joined = new StringJoiner(PARAMETER_SEPARATOR, QUERY_PREFIX, "");
        parameters.forEach((name, value) -> {
            if (value != null) {
                joined.add(encode(name) + VALUE_SEPARATOR + encode(value));
            }
        });
        return joined.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
