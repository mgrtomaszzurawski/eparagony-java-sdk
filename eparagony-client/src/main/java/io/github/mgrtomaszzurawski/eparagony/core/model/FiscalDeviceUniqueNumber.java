package io.github.mgrtomaszzurawski.eparagony.core.model;

import java.util.Objects;

/**
 * The unique number of a fiscal device, as burned in by its manufacturer — for example
 * {@code ZBN1901007833}. Addresses a printer when querying its status or its daily reports, and is
 * reported back on every confirmed document.
 */
public record FiscalDeviceUniqueNumber(String value) {

    public FiscalDeviceUniqueNumber {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("fiscalDeviceUniqueNumber must not be blank");
        }
    }

    public static FiscalDeviceUniqueNumber of(String value) {
        return new FiscalDeviceUniqueNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
