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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The reported state of a fiscal printer.
 *
 * @param state whether the device is currently reachable
 * @param lastActiveAt when it was last seen, if ever
 * @param crkLastConnectedAt when it last connected to the CRK — the national fiscal repository — if
 *     ever. A device that fiscalizes but has not reached the CRK in a long time is a compliance
 *     problem worth alerting on, which is why this is surfaced rather than hidden.
 */
public record PrinterStatus(PrinterState state, Instant lastActiveAt, Instant crkLastConnectedAt) {

    public PrinterStatus {
        Objects.requireNonNull(state, "state");
    }

    public Optional<Instant> lastActiveAtIfPresent() {
        return Optional.ofNullable(lastActiveAt);
    }

    public Optional<Instant> crkLastConnectedAtIfPresent() {
        return Optional.ofNullable(crkLastConnectedAt);
    }
}
