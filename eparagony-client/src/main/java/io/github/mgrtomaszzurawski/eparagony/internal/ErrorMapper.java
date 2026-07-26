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
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyValidationException;

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
        String detail = describe(statusCode, body, path);
        return switch (statusCode) {
            case HTTP_BAD_REQUEST -> new EparagonyValidationException(detail, extractErrorCode(body));
            case HTTP_UNAUTHORIZED -> new EparagonyAuthException(detail);
            case HTTP_FORBIDDEN -> new EparagonyAccessDeniedException(detail + ACCESS_DENIED_HINT);
            case HTTP_NOT_FOUND -> new EparagonyNotFoundException(detail);
            case HTTP_UNPROCESSABLE_ENTITY -> new EparagonyIdempotencyException(detail);
            default -> statusCode >= HTTP_SERVER_ERROR_MIN
                    ? new EparagonyServerException(detail, statusCode, !idempotent)
                    : new EparagonyException(detail);
        };
    }

    private String describe(int statusCode, String body, String path) {
        String serverMessage = extractMessage(body);
        return serverMessage == null
                ? "HTTP " + statusCode + " from " + path
                : "HTTP " + statusCode + " from " + path + ": " + serverMessage;
    }

    private String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = codec.readTree(body);
        } catch (EparagonyException notJson) {
            // A gateway can answer with HTML; the status alone still carries the remediation.
            return null;
        }
        for (String field : new String[] {FIELD_MESSAGE, FIELD_ERROR_DESCRIPTION, FIELD_ERROR}) {
            JsonNode candidate = root.get(field);
            if (candidate != null && candidate.isTextual() && !candidate.asText().isBlank()) {
                return candidate.asText();
            }
        }
        return null;
    }

    private Integer extractErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode candidate = codec.readTree(body).get(FIELD_ERROR_CODE);
            return candidate != null && candidate.isNumber() ? candidate.asInt() : null;
        } catch (EparagonyException notJson) {
            return null;
        }
    }
}
