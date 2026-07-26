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
package io.github.mgrtomaszzurawski.eparagony.internal.client.printers;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.DailyReport;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.DailyReportCounters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;

/** Reads a daily fiscal report payload. Package-private. */
final class DailyReportMapper {

    private static final String FIELD_ISSUED_AT = "issuedAt";
    private static final String FIELD_SALE_FROM = "saleFrom";
    private static final String FIELD_SALE_TO = "saleTo";
    private static final String FIELD_REPORT_NUMBER = "reportNumber";
    private static final String FIELD_TAX_RATES = "taxRates";
    private static final String FIELD_INVOICES = "invoices";
    private static final String FIELD_SALE_GROSS = "saleGross";
    private static final String FIELD_SALE = "sale";
    private static final String FIELD_TAX = "tax";
    private static final String FIELD_SALE_TOTAL = "saleTotal";
    private static final String FIELD_TAX_TOTAL = "taxTotal";

    private static final String FIELD_EMERGENCY_SITUATIONS = "emergencySituationsCount";
    private static final String FIELD_CANCELED_RECEIPTS = "canceledReceiptsCount";
    private static final String FIELD_NON_FISCAL_DOCUMENTS = "nonFiscalDocumentsCount";
    private static final String FIELD_RECEIPTS = "receiptsCount";
    private static final String FIELD_PROGRAMMING_EVENTS = "programmingEventsCount";
    private static final String FIELD_DB_CHANGES = "dbChangesCount";
    private static final String FIELD_NON_FISCAL_ERRORS = "nonFiscalErrorsCount";
    private static final String FIELD_COMMUNICATION_ERRORS = "communicationErrorsCount";

    private static final int ABSENT_COUNT = 0;

    private DailyReportMapper() {
    }

    static DailyReport fromJson(JsonNode report) {
        return new DailyReport(
                instant(report, FIELD_ISSUED_AT),
                instant(report, FIELD_SALE_FROM),
                instant(report, FIELD_SALE_TO),
                integer(report, FIELD_REPORT_NUMBER),
                textPerTaxRate(report.get(FIELD_TAX_RATES)),
                decimalPerTaxRate(report.get(FIELD_INVOICES)),
                decimalPerTaxRate(report.get(FIELD_SALE_GROSS)),
                decimalPerTaxRate(report.get(FIELD_SALE)),
                decimalPerTaxRate(report.get(FIELD_TAX)),
                decimal(report, FIELD_SALE_TOTAL),
                decimal(report, FIELD_TAX_TOTAL),
                counters(report));
    }

    private static DailyReportCounters counters(JsonNode report) {
        return new DailyReportCounters(
                integer(report, FIELD_EMERGENCY_SITUATIONS),
                integer(report, FIELD_CANCELED_RECEIPTS),
                integer(report, FIELD_NON_FISCAL_DOCUMENTS),
                integer(report, FIELD_RECEIPTS),
                integer(report, FIELD_PROGRAMMING_EVENTS),
                integer(report, FIELD_DB_CHANGES),
                integer(report, FIELD_NON_FISCAL_ERRORS),
                integer(report, FIELD_COMMUNICATION_ERRORS));
    }

    /** The rates in force, keyed by slot. Values stay strings because {@code "ZW"} is one of them. */
    private static Map<TaxRateCode, String> textPerTaxRate(JsonNode node) {
        EnumMap<TaxRateCode, String> values = new EnumMap<>(TaxRateCode.class);
        if (node == null || !node.isObject()) {
            return values;
        }
        for (TaxRateCode code : TaxRateCode.values()) {
            JsonNode value = node.get(code.name());
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                values.put(code, value.asText());
            }
        }
        return values;
    }

    /**
     * Amounts by slot. The register reports these as decimal strings in złoty, so they are kept as
     * {@link BigDecimal} rather than converted to grosze — converting would assert a precision the
     * device did not give.
     */
    private static Map<TaxRateCode, BigDecimal> decimalPerTaxRate(JsonNode node) {
        EnumMap<TaxRateCode, BigDecimal> values = new EnumMap<>(TaxRateCode.class);
        if (node == null || !node.isObject()) {
            return values;
        }
        for (TaxRateCode code : TaxRateCode.values()) {
            BigDecimal value = decimal(node, code.name());
            if (value != null) {
                values.put(code, value);
            }
        }
        return values;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText().trim());
        } catch (NumberFormatException notANumber) {
            // A malformed figure is dropped rather than failing the whole report: a monitoring call
            // that returns fifty reports should not be lost to one bad cell.
            return null;
        }
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : ABSENT_COUNT;
    }

    private static Instant instant(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(value.asText());
        } catch (DateTimeParseException unparseable) {
            return null;
        }
    }
}
