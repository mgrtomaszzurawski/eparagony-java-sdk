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

import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A daily fiscal report — the register's own end-of-day summary, and the document a tax inspection
 * asks for.
 *
 * <p>Amounts are {@link BigDecimal} in złoty here rather than {@code Amount} in grosze, because that
 * is how the register reports them: as decimal strings. Converting would be inventing precision the
 * device did not give.
 *
 * @param issuedAt when the report was produced
 * @param saleFrom start of the sales period it covers, when the register reports one
 * @param saleTo end of that period, when the register reports one
 * @param reportNumber the register's sequential report number
 * @param taxRates the rates in force when the report was produced, by slot
 * @param invoices invoiced amounts by slot
 * @param saleGross gross sales by slot
 * @param sale net sales by slot
 * @param tax tax by slot
 * @param saleTotal total sales across all slots
 * @param taxTotal total tax across all slots
 * @param counters the register's event counters — receipts, cancellations, errors and the rest
 */
public record DailyReport(
        Instant issuedAt,
        Instant saleFrom,
        Instant saleTo,
        int reportNumber,
        Map<TaxRateCode, String> taxRates,
        Map<TaxRateCode, BigDecimal> invoices,
        Map<TaxRateCode, BigDecimal> saleGross,
        Map<TaxRateCode, BigDecimal> sale,
        Map<TaxRateCode, BigDecimal> tax,
        BigDecimal saleTotal,
        BigDecimal taxTotal,
        DailyReportCounters counters) {

    public DailyReport {
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(counters, "counters");
        taxRates = Map.copyOf(Objects.requireNonNullElse(taxRates, Map.of()));
        invoices = Map.copyOf(Objects.requireNonNullElse(invoices, Map.of()));
        saleGross = Map.copyOf(Objects.requireNonNullElse(saleGross, Map.of()));
        sale = Map.copyOf(Objects.requireNonNullElse(sale, Map.of()));
        tax = Map.copyOf(Objects.requireNonNullElse(tax, Map.of()));
    }

    public Optional<Instant> saleFromIfReported() {
        return Optional.ofNullable(saleFrom);
    }

    public Optional<Instant> saleToIfReported() {
        return Optional.ofNullable(saleTo);
    }

    public Optional<BigDecimal> saleTotalIfReported() {
        return Optional.ofNullable(saleTotal);
    }

    public Optional<BigDecimal> taxTotalIfReported() {
        return Optional.ofNullable(taxTotal);
    }
}
