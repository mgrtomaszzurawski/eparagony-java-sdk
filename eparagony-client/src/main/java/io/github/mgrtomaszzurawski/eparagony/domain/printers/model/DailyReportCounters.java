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
 * The event counters a register reports with its daily summary.
 *
 * <p>Grouped into their own type rather than flattened onto {@link DailyReport}: they are nine
 * integers that only ever move together, and a record with twenty-odd components is a record nobody
 * reads. Non-zero error counters are the ones worth alerting on.
 *
 * @param emergencySituations emergency situations recorded by the device
 * @param canceledReceipts receipts cancelled before fiscalization
 * @param nonFiscalDocuments non-fiscal documents printed
 * @param receipts fiscal receipts issued
 * @param programmingEvents programming events, e.g. tax-rate reconfiguration
 * @param databaseChanges changes to the device's product database
 * @param nonFiscalErrors non-fiscal errors
 * @param communicationErrors communication errors, typically toward the repository
 */
public record DailyReportCounters(
        int emergencySituations,
        int canceledReceipts,
        int nonFiscalDocuments,
        int receipts,
        int programmingEvents,
        int databaseChanges,
        int nonFiscalErrors,
        int communicationErrors) {

    /** {@code true} when any error or emergency counter is non-zero — worth a look. */
    public boolean hasAnomalies() {
        return emergencySituations > 0 || nonFiscalErrors > 0 || communicationErrors > 0;
    }
}
