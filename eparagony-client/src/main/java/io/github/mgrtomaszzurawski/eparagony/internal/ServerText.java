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
 * Prepares a server-supplied string for an exception message. Internal: never exported.
 *
 * <p>One place, for the same reason {@link JsonReader} is one place. Five call sites had grown five
 * near-identical copies of this and they had already diverged: all five bounded the length, only two
 * stripped line breaks. Every one of these strings is chosen by the far side of the wire and ends up
 * in somebody's log, so "most of them are hardened" is the same as none.
 *
 * <p>Two rules, applied together:
 * <ul>
 *   <li><strong>Bounded.</strong> An error body is not a size the SDK controls, and a megabyte of it
 *       in a stack trace helps nobody.
 *   <li><strong>Single-line.</strong> A CR or LF in a logged value lets the server forge what looks
 *       like a separate log entry.
 * </ul>
 */
public final class ServerText {

    /** Long enough to carry a real diagnostic, short enough not to flood a log line. */
    private static final int MAX_LENGTH = 200;
    private static final String ELLIPSIS = "...";

    private ServerText() {
    }

    /** The value, flattened to one line and bounded; {@code null} maps to {@code null}. */
    public static String safe(String value) {
        if (value == null) {
            return null;
        }
        String flattened = value.replace('\r', ' ').replace('\n', ' ');
        return flattened.length() <= MAX_LENGTH
                ? flattened
                : flattened.substring(0, MAX_LENGTH) + ELLIPSIS;
    }

    /** The same, quoted, with an explicit stand-in when the server sent nothing to quote. */
    public static String quoted(String value, String absentDescription) {
        return value == null ? absentDescription : "\"" + safe(value) + "\"";
    }
}
