package io.github.mgrtomaszzurawski.eparagony.domain.printers.model;

/**
 * Whether a fiscal printer is currently reachable.
 *
 * <p>Unrecognised values map to {@link #UNKNOWN} rather than failing, on the same reasoning as
 * elsewhere in the SDK: the API may add states without notice, and a monitoring call is not worth
 * throwing over.
 */
public enum PrinterState {

    /** Connected and able to fiscalize. */
    ACTIVE,

    /** Not currently connected. */
    INACTIVE,

    /** A state this SDK does not recognise. */
    UNKNOWN;

    /** Maps a wire value, never throwing — see {@link #UNKNOWN}. */
    public static PrinterState fromWireValue(String wireValue) {
        if (wireValue == null) {
            return UNKNOWN;
        }
        for (PrinterState state : values()) {
            if (state != UNKNOWN && state.name().equalsIgnoreCase(wireValue.trim())) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
