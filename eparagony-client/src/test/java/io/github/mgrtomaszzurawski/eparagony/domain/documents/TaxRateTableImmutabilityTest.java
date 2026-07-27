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
package io.github.mgrtomaszzurawski.eparagony.domain.documents;

import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves that the defensive copy in the record's compact constructor actually takes effect.
 *
 * <p>This test earns its keep twice. It guards a real property — a caller must not be able to mutate
 * a receipt's tax table after the fact — and it is the evidence behind suppressing PMD's
 * {@code UnusedAssignment} on records, which reads the compact-constructor assignment as dead code.
 * If PMD were right, both assertions below would fail.
 */
class TaxRateTableImmutabilityTest {

    @Test
    @DisplayName("copies the caller's map instead of retaining it")
    void copiesCallerMap() {
        EnumMap<TaxRateCode, String> mutable = new EnumMap<>(TaxRateCode.class);
        for (TaxRateCode code : TaxRateCode.values()) {
            mutable.put(code, "0");
        }
        mutable.put(TaxRateCode.A, "23");

        TaxRateTable table = new TaxRateTable(mutable);
        // Mutating the source afterwards must not reach into the constructed table.
        mutable.put(TaxRateCode.A, "8");

        assertEquals("23", table.rateFor(TaxRateCode.A));
    }

    @Test
    @DisplayName("exposes an unmodifiable view")
    void exposesUnmodifiableView() {
        Map<TaxRateCode, String> rates = TaxRateTable.standardPolish().rates();

        assertThrows(UnsupportedOperationException.class, () -> rates.put(TaxRateCode.A, "8"));
    }

    @Test
    @DisplayName("derives a new table without touching the original")
    void withDoesNotMutate() {
        TaxRateTable original = TaxRateTable.standardPolish();

        TaxRateTable derived = original.with(TaxRateCode.B, "7");

        assertEquals("8", original.rateFor(TaxRateCode.B));
        assertEquals("7", derived.rateFor(TaxRateCode.B));
    }
}
