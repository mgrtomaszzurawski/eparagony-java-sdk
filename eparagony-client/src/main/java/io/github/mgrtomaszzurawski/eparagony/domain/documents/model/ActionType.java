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

/**
 * An asynchronous process eparagony.pl can run for a document, beyond issuing it.
 *
 * <p>Unrecognised values map to {@link #UNKNOWN}: the API adds actions without notice, and a
 * notification about an action this SDK has not heard of is still worth delivering to the caller.
 */
public enum ActionType {

    /** Delivering the receipt to the Allegro marketplace. */
    DELIVER_VIA_ALLEGRO,

    /** An action type this SDK does not recognise. */
    UNKNOWN;

    /** Maps a wire value, never throwing — see {@link #UNKNOWN}. */
    public static ActionType fromWireValue(String wireValue) {
        if (wireValue == null) {
            return UNKNOWN;
        }
        for (ActionType type : values()) {
            if (type != UNKNOWN && type.name().equalsIgnoreCase(wireValue.trim())) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
