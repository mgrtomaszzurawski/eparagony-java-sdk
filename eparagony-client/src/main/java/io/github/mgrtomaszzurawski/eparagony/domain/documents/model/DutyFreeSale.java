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

import java.util.List;
import java.util.Objects;

/**
 * Marks the sale as duty-free and records the journey that justifies it.
 *
 * <p>The destination and any intermediate stops are what an inspection checks the exemption against,
 * which is why they are printed on the receipt rather than kept in the seller's system.
 *
 * @param destination where the traveller is going
 * @param stops intermediate stops on the way, in order
 */
public record DutyFreeSale(String destination, List<String> stops) {

    public DutyFreeSale {
        Objects.requireNonNull(destination, "destination");
        if (destination.isBlank()) {
            throw new IllegalArgumentException("a duty-free sale must state its destination");
        }
        stops = List.copyOf(Objects.requireNonNullElse(stops, List.of()));
    }

    /** A direct journey, with no intermediate stops. */
    public static DutyFreeSale to(String destination) {
        return new DutyFreeSale(destination, List.of());
    }

    /** A journey via the given stops, in order. */
    public static DutyFreeSale to(String destination, List<String> stops) {
        return new DutyFreeSale(destination, stops);
    }
}
