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

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * What each of the cash register's VAT slots currently means. Every slot {@code A}–{@code G} must be
 * declared, even the unused ones, because the API requires all seven.
 *
 * <p>The values must match how the register is actually programmed, and in the same slot order. A
 * mismatch does not fail loudly — it fiscalizes the sale at the wrong rate.
 *
 * <p>Rates are strings, not numbers, because {@code "ZW"} (<em>zwolniony</em>, exempt) is a legal
 * value alongside {@code "23"} and {@code "8"}.
 */
public record TaxRateTable(Map<TaxRateCode, String> rates) {

    /** The value denoting a VAT exemption rather than a percentage. */
    public static final String EXEMPT = "ZW";

    /** The unused-slot convention: a zero rate. */
    private static final String UNUSED = "0";

    private static final String STANDARD_A = "23";
    private static final String STANDARD_B = "8";
    private static final String STANDARD_C = "5";
    private static final String STANDARD_D = "0";

    public TaxRateTable {
        Objects.requireNonNull(rates, "rates");
        EnumMap<TaxRateCode, String> copy = new EnumMap<>(TaxRateCode.class);
        copy.putAll(rates);
        for (TaxRateCode code : TaxRateCode.values()) {
            String rate = copy.get(code);
            if (rate == null || rate.isBlank()) {
                throw new IllegalArgumentException(
                        "tax rate table must declare every slot A-G; slot " + code + " is missing");
            }
        }
        rates = Map.copyOf(copy);
    }

    /**
     * The configuration used by the overwhelming majority of Polish businesses:
     * {@code A}=23, {@code B}=8, {@code C}=5, {@code D}=0, {@code E}=exempt, {@code F} and {@code G}
     * unused.
     *
     * <p>Convenient, but confirm it against the actual register before relying on it in production.
     */
    public static TaxRateTable standardPolish() {
        EnumMap<TaxRateCode, String> rates = new EnumMap<>(TaxRateCode.class);
        rates.put(TaxRateCode.A, STANDARD_A);
        rates.put(TaxRateCode.B, STANDARD_B);
        rates.put(TaxRateCode.C, STANDARD_C);
        rates.put(TaxRateCode.D, STANDARD_D);
        rates.put(TaxRateCode.E, EXEMPT);
        rates.put(TaxRateCode.F, UNUSED);
        rates.put(TaxRateCode.G, UNUSED);
        return new TaxRateTable(rates);
    }

    /** Starts from {@link #standardPolish()} and overrides individual slots. */
    public TaxRateTable with(TaxRateCode code, String rate) {
        EnumMap<TaxRateCode, String> copy = new EnumMap<>(rates);
        copy.put(Objects.requireNonNull(code, "code"), Objects.requireNonNull(rate, "rate"));
        return new TaxRateTable(copy);
    }

    /** The rate configured in a given slot. */
    public String rateFor(TaxRateCode code) {
        return rates.get(code);
    }
}
