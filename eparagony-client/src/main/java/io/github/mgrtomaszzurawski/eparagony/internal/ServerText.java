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

    private static final int NEXT_LINE = 0x0085;
    private static final int LINE_SEPARATOR = 0x2028;
    private static final int PARAGRAPH_SEPARATOR = 0x2029;

    private ServerText() {
    }

    /** The value, flattened to one line and bounded; {@code null} maps to {@code null}. */
    public static String safe(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder flattened = new StringBuilder(Math.min(value.length(), MAX_LENGTH + 1));
        value.codePoints().forEach(codePoint -> flattened.appendCodePoint(
                isLineBreakOrControl(codePoint) ? ' ' : codePoint));
        return truncate(flattened.toString());
    }

    /**
     * Every character a reader or a tool might treat as a break, not only {@code \r} and {@code \n}.
     *
     * <p>The log-forging attack needs {@code \n}, and stripping that alone defeats it. The rest are
     * here because "flattened to one line" should be true rather than nearly true: {@code less} and a
     * terminal render VT, FF, NEL, U+2028 and U+2029 as breaks, and {@code Scanner} splits on them.
     * ESC goes too — a value that reaches a terminal should not be able to move the cursor or set
     * colours — as do lone surrogates and the bidi format characters.
     */
    private static boolean isLineBreakOrControl(int codePoint) {
        if (codePoint == '\n' || codePoint == '\r' || codePoint == NEXT_LINE
                || codePoint == LINE_SEPARATOR || codePoint == PARAGRAPH_SEPARATOR) {
            return true;
        }
        int category = Character.getType(codePoint);
        // CONTROL covers Cc. SURROGATE catches a lone surrogate the server sent — truncation no
        // longer creates one, but Jackson will decode a bare \ud800 without checking it is paired,
        // and a JSON log encoder throws on it, losing the line that recorded the value. FORMAT
        // catches U+202E and the bidi isolates, which let a value visually reorder its own log line.
        return category == Character.CONTROL || category == Character.SURROGATE
                || category == Character.FORMAT;
    }

    /**
     * Cuts on a character boundary, never through a surrogate pair.
     *
     * <p>A lone surrogate is not valid text: a JSON log encoder throws on it, which would suppress the
     * very line recording the suspicious value.
     */
    private static String truncate(String value) {
        if (value.length() <= MAX_LENGTH) {
            return value;
        }
        int cutPoint = Character.isHighSurrogate(value.charAt(MAX_LENGTH - 1))
                ? MAX_LENGTH - 1
                : MAX_LENGTH;
        return value.substring(0, cutPoint) + ELLIPSIS;
    }

    /** The same, quoted, with an explicit stand-in when the server sent nothing to quote. */
    public static String quoted(String value, String absentDescription) {
        return value == null ? absentDescription : "\"" + safe(value) + "\"";
    }
}
