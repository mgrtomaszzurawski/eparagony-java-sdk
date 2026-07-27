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

/**
 * A successful response, kept whole because the status code carries meaning of its own. Internal:
 * never exported.
 *
 * <p>{@code POST /documents} distinguishes {@code 200} from {@code 202} — the first means nothing was
 * ordered on the printer, the second that fiscalization is under way — so a decoder that saw only the
 * body could not tell the caller which happened.
 */
public record RawResponse(int statusCode, String body) {
}
