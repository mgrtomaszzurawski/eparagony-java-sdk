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
