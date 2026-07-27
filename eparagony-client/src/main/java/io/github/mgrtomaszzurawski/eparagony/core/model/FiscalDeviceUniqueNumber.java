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
