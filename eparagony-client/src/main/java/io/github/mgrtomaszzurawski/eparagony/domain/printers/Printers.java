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
package io.github.mgrtomaszzurawski.eparagony.domain.printers;

import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.DailyReport;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterStatus;

import java.time.Instant;
import java.util.List;

/**
 * Reading the state of a fiscal printer. Obtained from {@code EparagonyClient.printers()}.
 *
 * <p>Scopes differ per method: {@link #status} requires {@code printer_get} and
 * {@link #dailyReports} requires {@code report_fiscal_get}. eparagony.pl grants both on request
 * rather than by default.
 */
public interface Printers {

    /**
     * Reads a printer's current status.
     *
     * <p>Intended for monitoring — the natural use is an alert when a till goes {@code INACTIVE}
     * during trading hours, before anyone tries to fiscalize against it.
     */
    PrinterStatus status(FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber);

    /**
     * Lists a printer's daily fiscal reports, optionally narrowed to a date range.
     *
     * <p><strong>The server returns at most 50 reports per call</strong> and offers no cursor, so a
     * wider range does not page — it truncates. Narrow the range rather than expecting continuation;
     * fifty days is the practical window.
     *
     * <p>Requires the {@code report_fiscal_get} scope, which eparagony.pl grants on request.
     *
     * @param issuedFrom inclusive lower bound on issuance, or {@code null} for no lower bound
     * @param issuedTo inclusive upper bound on issuance, or {@code null} for no upper bound
     */
    List<DailyReport> dailyReports(FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber,
            Instant issuedFrom, Instant issuedTo);
}
