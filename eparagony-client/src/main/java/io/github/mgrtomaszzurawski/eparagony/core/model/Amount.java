package io.github.mgrtomaszzurawski.eparagony.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A monetary amount in <em>grosze</em> — hundredths of a złoty — which is the only form the API
 * accepts. {@code 10000} is 100.00 PLN.
 *
 * <p>This type exists to stop the single most expensive mistake in a fiscal integration: passing a
 * złoty figure where grosze are expected, understating every document by a factor of a hundred. A
 * bare {@code int} parameter accepts both and complains about neither.
 *
 * <p>Negative amounts are permitted — corrective invoices need them — so the type does not police
 * sign. It polices <em>units</em>.
 */
public record Amount(int grosze) {

    private static final int GROSZE_PER_ZLOTY = 100;
    private static final int ZLOTY_SCALE = 2;

    /** Wraps a figure that is already in grosze. */
    public static Amount ofGrosze(int grosze) {
        return new Amount(grosze);
    }

    /**
     * Converts from złoty. Rejects anything with sub-grosz precision rather than rounding it: a
     * silently rounded fiscal total is a discrepancy nobody notices until an audit.
     */
    public static Amount ofZloty(BigDecimal zloty) {
        BigDecimal exact;
        try {
            exact = zloty.setScale(ZLOTY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException tooPrecise) {
            throw new IllegalArgumentException(
                    "amount " + zloty + " PLN has sub-grosz precision and cannot be represented exactly",
                    tooPrecise);
        }
        return new Amount(exact.movePointRight(ZLOTY_SCALE).intValueExact());
    }

    /** The amount as złoty, exact to two decimal places. */
    public BigDecimal toZloty() {
        return BigDecimal.valueOf(grosze, ZLOTY_SCALE);
    }

    @Override
    public String toString() {
        return toZloty().toPlainString() + " PLN";
    }

    /** Grosze per złoty, for callers doing their own arithmetic against this scale. */
    public static int groszePerZloty() {
        return GROSZE_PER_ZLOTY;
    }
}
