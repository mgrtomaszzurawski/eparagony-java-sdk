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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmountTest {

    @Test
    @DisplayName("converts złoty to grosze exactly")
    void convertsZlotyToGrosze() {
        assertEquals(10000, Amount.ofZloty(new BigDecimal("100.00")).grosze());
        assertEquals(1, Amount.ofZloty(new BigDecimal("0.01")).grosze());
        assertEquals(6482, Amount.ofZloty(new BigDecimal("64.82")).grosze());
    }

    @Test
    @DisplayName("rejects sub-grosz precision instead of rounding it away")
    void rejectsSubGroszPrecision() {
        // Rounding here would produce a document whose total silently disagrees with the source
        // system by a grosz — the kind of discrepancy nobody finds until an audit.
        BigDecimal subGrosz = new BigDecimal("10.005");

        assertThrows(IllegalArgumentException.class, () -> Amount.ofZloty(subGrosz));
    }

    @Test
    @DisplayName("round-trips through złoty")
    void roundTrips() {
        assertEquals(new BigDecimal("100.00"), Amount.ofGrosze(10000).toZloty());
        assertEquals(new BigDecimal("0.07"), Amount.ofGrosze(7).toZloty());
    }

    @Test
    @DisplayName("allows negative amounts, which corrections need")
    void allowsNegative() {
        assertEquals(-500, Amount.ofGrosze(-500).grosze());
    }

    @Test
    @DisplayName("renders as złoty so a log line is readable")
    void rendersAsZloty() {
        assertEquals("100.00 PLN", Amount.ofGrosze(10000).toString());
    }
}
