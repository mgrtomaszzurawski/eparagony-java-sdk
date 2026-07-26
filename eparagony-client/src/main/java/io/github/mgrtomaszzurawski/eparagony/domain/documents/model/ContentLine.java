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
package io.github.mgrtomaszzurawski.eparagony.domain.documents.model;

import java.util.Objects;
import java.util.Optional;

/**
 * One line of seller-defined content printed on the document — the advertising, notices and codes a
 * receipt carries beneath the fiscal part.
 *
 * <p>Five shapes, one type, because that is how the API models it: a {@code oneOf} discriminated on
 * {@code type}. The factory methods are the whole public surface, so an invalid combination — a
 * separator with a body, a QR code without one — cannot be constructed.
 *
 * <p>This is not the same thing as a product line's {@code additionalDescription}, which is a simpler
 * text-or-graphic pair. The API distinguishes them and so does the SDK.
 *
 * @param type which shape this line takes
 * @param key the label, for a {@link Type#KEY_VALUE} line
 * @param value the value, for a {@link Type#KEY_VALUE} line
 * @param body the content, for text, barcode and QR lines
 */
public record ContentLine(Type type, String key, String value, String body) {

    /** The kinds of line a document's printed content can contain. */
    public enum Type {

        /** Free text. */
        TEXT("TEXT"),

        /** A labelled value, printed as a pair. */
        KEY_VALUE("KEY_VALUE"),

        /** A barcode rendered from {@code body}. */
        BARCODE("BARCODE"),

        /** A QR code rendered from {@code body}. */
        QR_CODE("QR_CODE"),

        /** A horizontal rule. Carries nothing else. */
        SEPARATOR("SEPARATOR");

        private final String wireValue;

        Type(String wireValue) {
            this.wireValue = wireValue;
        }

        /** The literal the API expects in the {@code type} discriminator. */
        public String wireValue() {
            return wireValue;
        }
    }

    public ContentLine {
        Objects.requireNonNull(type, "type");
    }

    /** A line of free text. */
    public static ContentLine text(String body) {
        return new ContentLine(Type.TEXT, null, null, requireContent(body, "body"));
    }

    /** A labelled value, e.g. {@code "Numer zamówienia"} / {@code "ORDER-1183"}. */
    public static ContentLine keyValue(String key, String value) {
        return new ContentLine(Type.KEY_VALUE, requireContent(key, "key"),
                requireContent(value, "value"), null);
    }

    /** A barcode rendered from {@code body}. */
    public static ContentLine barcode(String body) {
        return new ContentLine(Type.BARCODE, null, null, requireContent(body, "body"));
    }

    /** A QR code rendered from {@code body} — a loyalty link, a survey, a return form. */
    public static ContentLine qrCode(String body) {
        return new ContentLine(Type.QR_CODE, null, null, requireContent(body, "body"));
    }

    /** A horizontal rule. */
    public static ContentLine separator() {
        return new ContentLine(Type.SEPARATOR, null, null, null);
    }

    public Optional<String> keyIfPresent() {
        return Optional.ofNullable(key);
    }

    public Optional<String> valueIfPresent() {
        return Optional.ofNullable(value);
    }

    public Optional<String> bodyIfPresent() {
        return Optional.ofNullable(body);
    }

    private static String requireContent(String candidate, String name) {
        Objects.requireNonNull(candidate, name);
        if (candidate.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank; it is printed on the receipt");
        }
        return candidate;
    }
}
