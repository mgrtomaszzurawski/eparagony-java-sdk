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
 * separator with a body, a key-value line without a key — cannot be constructed.
 *
 * <p>The barcode case is the one worth reading. There is no {@code "BARCODE"} discriminator: the
 * {@code type} <em>is</em> the symbology, one of nineteen values from {@code EAN13} to
 * {@code PHARMACODE}. {@link #barcode(BarcodeSymbology, String)} therefore takes the symbology rather
 * than defaulting to one, because a receipt printed with the wrong symbology scans as the wrong
 * number or does not scan at all.
 *
 * <p>This is not the same thing as a product line's {@code additionalDescription}, which is a simpler
 * text-or-graphic pair. The API distinguishes them and so does the SDK.
 *
 * @param type the discriminator this line carries, which for a barcode is its symbology
 * @param key the label, for a key-value line
 * @param value the value, for a key-value line
 * @param body the content, for text, barcode and QR lines
 */
public record ContentLine(String type, String key, String value, String body) {

    /** The discriminator for a plain text line. */
    public static final String TYPE_TEXT = "TEXT";

    /** The discriminator for a QR code. Note {@code QR}, not {@code QR_CODE}. */
    public static final String TYPE_QR = "QR";

    /** The discriminator for a horizontal rule. */
    public static final String TYPE_SEPARATOR = "SEPARATOR";

    /** The discriminator for a labelled value. */
    public static final String TYPE_KEY_VALUE = "KEY_VALUE";

    /**
     * The barcode symbologies the API accepts. The chosen value travels as the line's {@code type};
     * there is no separate "this is a barcode" marker.
     */
    public enum BarcodeSymbology {
        CODE39, CODE128, CODE128A, CODE128B, CODE128C,
        EAN13, EAN8, EAN5, EAN2, UPC,
        ITF14, ITF, MSI, MSI10, MSI11, MSI1010, MSI1110,
        PHARMACODE, CODABAR;

        /** The literal the API expects in the {@code type} discriminator. */
        public String wireValue() {
            return name();
        }
    }

    public ContentLine {
        Objects.requireNonNull(type, "type");
    }

    /** A line of free text. */
    public static ContentLine text(String body) {
        return new ContentLine(TYPE_TEXT, null, null, requireContent(body, "body"));
    }

    /** A labelled value, e.g. {@code "Numer zamówienia"} / {@code "ORDER-1183"}. */
    public static ContentLine keyValue(String key, String value) {
        return new ContentLine(TYPE_KEY_VALUE, requireContent(key, "key"),
                requireContent(value, "value"), null);
    }

    /**
     * A barcode in the given symbology. Pick the one the reader on the other end expects — an EAN
     * printed as {@code CODE128} is not the same barcode.
     */
    public static ContentLine barcode(BarcodeSymbology symbology, String body) {
        Objects.requireNonNull(symbology, "symbology");
        return new ContentLine(symbology.wireValue(), null, null, requireContent(body, "body"));
    }

    /**
     * A QR code. A body in URL form is additionally rendered as a link — which is what makes this the
     * natural place for a returns form or a loyalty account.
     */
    public static ContentLine qrCode(String body) {
        return new ContentLine(TYPE_QR, null, null, requireContent(body, "body"));
    }

    /** A horizontal rule. */
    public static ContentLine separator() {
        return new ContentLine(TYPE_SEPARATOR, null, null, null);
    }

    /** {@code true} when this line's {@code type} is one of the barcode symbologies. */
    public boolean isBarcode() {
        for (BarcodeSymbology symbology : BarcodeSymbology.values()) {
            if (symbology.wireValue().equals(type)) {
                return true;
            }
        }
        return false;
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
