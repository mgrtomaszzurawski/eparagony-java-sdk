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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The single chokepoint every server-supplied string passes through on its way into an exception
 * message. It exists to stop the far side of the wire choosing what lands in a consumer's log, so it
 * is worth more than the five copies it replaced only if it is actually tested.
 */
class ServerTextTest {

    private static final int MAX_LENGTH = 200;
    private static final String NEXT_LINE = "\u0085";
    private static final String LINE_SEPARATOR = "\u2028";
    private static final String PARAGRAPH_SEPARATOR = "\u2029";
    private static final String VERTICAL_TAB = "\u000B";
    private static final String FORM_FEED = "\f";
    private static final String ESCAPE = "\u001B";
    private static final String LONE_HIGH_SURROGATE = "\uD800";
    private static final String RIGHT_TO_LEFT_OVERRIDE = "\u202E";

    @Test
    @DisplayName("passes an ordinary value through untouched")
    void keepsOrdinaryText() {
        assertEquals("Access denied", ServerText.safe("Access denied"));
        assertNull(ServerText.safe(null));
    }

    @Test
    @DisplayName("flattens every character a log or a terminal would treat as a break")
    void flattensLineBreaks() {
        // The attack that motivates this: a value that forges what looks like a separate log entry.
        String forged = "ok\r\n2026-07-27 ERROR Payment reversed";

        String safe = ServerText.safe(forged);

        assertFalse(safe.contains("\n"), "a newline would forge a log entry: " + safe);
        assertFalse(safe.contains("\r"), "a carriage return would overwrite a log line: " + safe);
        assertTrue(safe.startsWith("ok  2026-07-27"), "but the text must survive: " + safe);
    }

    @Test
    @DisplayName("flattens the line terminators that are not carriage return or line feed")
    void flattensUnicodeLineTerminators() {
        // Written as escapes, not raw bytes: a control character pasted into a source file is one
        // stray reformat away from turning this test green for nothing.
        String awkward = "a" + NEXT_LINE + "b" + LINE_SEPARATOR + "c" + PARAGRAPH_SEPARATOR
                + "d" + VERTICAL_TAB + "e" + FORM_FEED + "f";

        String safe = ServerText.safe(awkward);

        // Each renders as a break in a terminal or in `less`, and Scanner splits on them.
        assertEquals("a b c d e f", safe);
    }

    @Test
    @DisplayName("strips the escape character so a value cannot drive a terminal")
    void stripsEscape() {
        String coloured = ESCAPE + "[31mALERT" + ESCAPE + "[0m";

        String safe = ServerText.safe(coloured);

        assertFalse(safe.contains(ESCAPE), "ESC must not survive: it repaints the reader's terminal");
        assertTrue(safe.contains("ALERT"));
    }

    @Test
    @DisplayName("bounds a value the server chose the length of")
    void boundsLength() {
        String huge = "x".repeat(10_000);

        String safe = ServerText.safe(huge);

        assertEquals(MAX_LENGTH + "...".length(), safe.length());
        assertTrue(safe.endsWith("..."));
    }

    @Test
    @DisplayName("cuts on a character boundary rather than through a surrogate pair")
    void neverSplitsASurrogatePair() {
        // A lone surrogate is not valid text: a JSON log encoder throws on it, which would suppress
        // the very line recording the value. The pair straddles the cut here by construction.
        String emoji = "😀";
        String straddling = "y".repeat(MAX_LENGTH - 1) + emoji + "tail";

        String safe = ServerText.safe(straddling);

        assertFalse(Character.isHighSurrogate(safe.charAt(safe.length() - "...".length() - 1)),
                "the cut left a lone high surrogate: " + safe.codePoints().count());
        assertTrue(safe.endsWith("..."));
    }

    @Test
    @DisplayName("flattens a lone surrogate and a bidi override the server sent")
    void flattensLoneSurrogateAndBidiOverride() {
        // Neither is created by truncation any more, but Jackson decodes a bare \ud800 without
        // checking it is paired, and U+202E lets a value visually reorder its own log line.
        String hostile = "a" + LONE_HIGH_SURROGATE + "b" + RIGHT_TO_LEFT_OVERRIDE + "c";

        String safe = ServerText.safe(hostile);

        assertEquals("a b c", safe);
    }

    @Test
    @DisplayName("quotes a present value and names an absent one")
    void quotesOrNames() {
        assertEquals("\"document_create\"", ServerText.quoted("document_create", "nothing"));
        assertEquals("nothing", ServerText.quoted(null, "nothing"));
    }

    @Test
    @DisplayName("sanitizes inside the quotes too")
    void quotedIsAlsoSanitized() {
        String safe = ServerText.quoted("a\nb", "absent");

        assertEquals("\"a b\"", safe);
    }
}
