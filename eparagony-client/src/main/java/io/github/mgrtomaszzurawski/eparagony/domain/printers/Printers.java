package io.github.mgrtomaszzurawski.eparagony.domain.printers;

import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterStatus;

/**
 * Reading the state of a fiscal printer. Obtained from {@code EparagonyClient.printers()}.
 *
 * <p>Requires the {@code printer_get} scope, which eparagony.pl grants on request rather than by
 * default.
 */
public interface Printers {

    /**
     * Reads a printer's current status.
     *
     * <p>Intended for monitoring — the natural use is an alert when a till goes {@code INACTIVE}
     * during trading hours, before anyone tries to fiscalize against it.
     */
    PrinterStatus status(FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber);
}
