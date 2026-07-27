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
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAccessDeniedException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyIdempotencyException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyNotFoundException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyRateLimitException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyValidationException;

import java.time.Duration;

/**
 * Turns a non-2xx response into the exception whose remediation matches. Internal: never exported.
 *
 * <p>Error bodies are small and uniformly shaped ({@code statusCode}, {@code error}, {@code message},
 * sometimes {@code errorCode}), so they are read as a tree rather than through a generated model —
 * one of them is not declared in the specification at all.
 */
public final class ErrorMapper {

    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_MIN = 500;

    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_ERROR_CODE = "errorCode";
    private static final String FIELD_ERROR_DESCRIPTION = "error_description";

    private static final String ACCESS_DENIED_HINT =
            " Check that the token's scope covers this endpoint, that the posId belongs to this client, "
                    + "and that the document was issued by it.";

    private final JsonCodec codec;

    public ErrorMapper(JsonCodec codec) {
        this.codec = codec;
    }

    /** Maps a failed response. {@code idempotent} decides whether a 5xx may have been applied. */
    public EparagonyException toException(int statusCode, String body, String path, boolean idempotent) {
        return toException(statusCode, body, path, idempotent, null);
    }

    /** Maps a failed response, carrying the server's {@code Retry-After} when it sent one. */
    public EparagonyException toException(int statusCode, String body, String path, boolean idempotent,
            Duration retryAfter) {
        // Parsed once. Both the message and the numeric code come out of the same body, and reading
        // the tree twice for one response is work done on every single failure.
        JsonNode root = parseOrNull(body);
        String detail = describe(statusCode, root, path);
        return switch (statusCode) {
            case HTTP_BAD_REQUEST -> new EparagonyValidationException(detail, extractErrorCode(root));
            case HTTP_UNAUTHORIZED -> new EparagonyAuthException(detail);
            case HTTP_FORBIDDEN -> new EparagonyAccessDeniedException(detail + ACCESS_DENIED_HINT);
            case HTTP_NOT_FOUND -> new EparagonyNotFoundException(detail);
            case HTTP_UNPROCESSABLE_ENTITY -> new EparagonyIdempotencyException(detail);
            case HTTP_TOO_MANY_REQUESTS -> new EparagonyRateLimitException(detail, retryAfter);
            default -> statusCode >= HTTP_SERVER_ERROR_MIN
                    ? new EparagonyServerException(detail, statusCode, !idempotent)
                    : new EparagonyException(detail);
        };
    }

    /** The body as a tree, or {@code null} when there is nothing readable in it. */
    private JsonNode parseOrNull(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return codec.readTree(body);
        } catch (EparagonyException notJson) {
            // A gateway can answer with HTML; the status alone still carries the remediation.
            return null;
        }
    }

    private String describe(int statusCode, JsonNode root, String path) {
        String serverMessage = extractMessage(root);
        return serverMessage == null
                ? "HTTP " + statusCode + " from " + path
                : "HTTP " + statusCode + " from " + path + ": " + serverMessage;
    }

    private static String extractMessage(JsonNode root) {
        if (root == null) {
            return null;
        }
        for (String field : new String[] {FIELD_MESSAGE, FIELD_ERROR_DESCRIPTION, FIELD_ERROR}) {
            JsonNode candidate = root.get(field);
            if (candidate != null && candidate.isTextual() && !candidate.asText().isBlank()) {
                return ServerText.safe(candidate.asText());
            }
        }
        return null;
    }

    private static Integer extractErrorCode(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode candidate = root.get(FIELD_ERROR_CODE);
        return candidate != null && candidate.isNumber() ? candidate.asInt() : null;
    }
}
