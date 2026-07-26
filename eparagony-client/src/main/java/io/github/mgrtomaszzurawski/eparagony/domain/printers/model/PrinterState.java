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
