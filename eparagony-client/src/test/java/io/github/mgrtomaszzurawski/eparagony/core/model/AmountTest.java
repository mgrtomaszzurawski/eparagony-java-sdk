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
        assertThrows(IllegalArgumentException.class, () -> Amount.ofZloty(new BigDecimal("10.005")));
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
