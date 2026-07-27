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

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * The warranty printed for a product, or for the whole receipt.
 *
 * <p>Expressed either as a period ({@code 24} {@link PeriodUnit#MONTH}s) or as an absolute end date —
 * the API accepts both, and which one a seller uses is a business decision, not a technical one.
 *
 * @param period how long the warranty runs, in {@link #periodUnit()}
 * @param periodUnit the unit the period is counted in
 * @param dateTo the date the warranty ends, when stated absolutely
 * @param additionalDescription free text printed alongside
 */
public record Warranty(Integer period, PeriodUnit periodUnit, OffsetDateTime dateTo,
        String additionalDescription) {

    /** The units a warranty period can be counted in. */
    public enum PeriodUnit {
        HOUR, DAY, MONTH, YEAR;

        /** The literal the API expects. Upper-case, matching the constant name. */
        public String wireValue() {
            return name();
        }
    }

    /** A warranty running for a period from the sale. */
    public static Warranty ofPeriod(int period, PeriodUnit periodUnit) {
        if (period <= 0) {
            throw new IllegalArgumentException("a warranty period must be positive but was " + period);
        }
        return new Warranty(period, Objects.requireNonNull(periodUnit, "periodUnit"), null, null);
    }

    /**
     * A warranty ending at a fixed instant. An offset is required, not merely a date: the
     * specification's own example is {@code 2019-09-02T23:59:59.999+02:00}, and a warranty that
     * expires "on the 2nd" means a different moment in Warsaw than in UTC.
     */
    public static Warranty until(OffsetDateTime dateTo) {
        return new Warranty(null, null, Objects.requireNonNull(dateTo, "dateTo"), null);
    }

    /** The same warranty with printed terms attached. */
    public Warranty describedAs(String additionalDescription) {
        return new Warranty(period, periodUnit, dateTo,
                Objects.requireNonNull(additionalDescription, "additionalDescription"));
    }

    public Optional<Integer> periodIfStated() {
        return Optional.ofNullable(period);
    }

    public Optional<OffsetDateTime> dateToIfStated() {
        return Optional.ofNullable(dateTo);
    }

    public Optional<String> additionalDescriptionIfStated() {
        return Optional.ofNullable(additionalDescription);
    }
}
