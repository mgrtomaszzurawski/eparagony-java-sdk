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
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.Printers;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.DailyReport;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterState;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterStatus;
import io.github.mgrtomaszzurawski.eparagony.internal.ApiPaths;
import io.github.mgrtomaszzurawski.eparagony.internal.HttpRuntime;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonCodec;
import io.github.mgrtomaszzurawski.eparagony.internal.PathTemplate;
import io.github.mgrtomaszzurawski.eparagony.internal.ScopeGuard;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Wires {@link Printers} onto the transport. Internal: never exported. */
public final class PrintersImpl implements Printers {

    private static final String PATH_PARAM_DEVICE = "fiscalDeviceUniqueNumber";


    private static final String FIELD_STATUS = "status";
    private static final String FIELD_LAST_ACTIVE_AT = "lastActiveAt";
    private static final String FIELD_CRK_STATUS = "crkStatus";
    private static final String FIELD_LAST_CONNECTED_AT = "lastConnectedAt";
    private static final String FIELD_REPORTS = "reports";

    private static final String QUERY_ISSUED_FROM = "issuedFrom";
    private static final String QUERY_ISSUED_TO = "issuedTo";

    private final HttpRuntime httpRuntime;
    private final JsonCodec codec;
    private final ScopeGuard scopeGuard;

    public PrintersImpl(HttpRuntime httpRuntime, JsonCodec codec, ScopeGuard scopeGuard) {
        this.httpRuntime = httpRuntime;
        this.codec = codec;
        this.scopeGuard = scopeGuard;
    }

    @Override
    public PrinterStatus status(FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber) {
        Objects.requireNonNull(fiscalDeviceUniqueNumber, PATH_PARAM_DEVICE);
        scopeGuard.require(Scope.PRINTER_GET, "printers().status()");
        String path = PathTemplate.expand(
                ApiPaths.PRINTER_STATUS, PATH_PARAM_DEVICE, fiscalDeviceUniqueNumber.value());
        JsonNode root = codec.readTree(httpRuntime.getRaw(path));
        return new PrinterStatus(
                PrinterState.fromWireValue(text(root, FIELD_STATUS)),
                instant(root, FIELD_LAST_ACTIVE_AT),
                crkLastConnectedAt(root));
    }

    @Override
    public List<DailyReport> dailyReports(FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber,
            Instant issuedFrom, Instant issuedTo) {
        Objects.requireNonNull(fiscalDeviceUniqueNumber, PATH_PARAM_DEVICE);
        scopeGuard.require(Scope.REPORT_FISCAL_GET, "printers().dailyReports()");
        if (issuedFrom != null && issuedTo != null && issuedFrom.isAfter(issuedTo)) {
            throw new IllegalArgumentException(
                    "issuedFrom " + issuedFrom + " is after issuedTo " + issuedTo);
        }
        String path = PathTemplate.expand(
                ApiPaths.PRINTER_DAILY_REPORTS, PATH_PARAM_DEVICE, fiscalDeviceUniqueNumber.value());
        Map<String, String> query = new LinkedHashMap<>();
        if (issuedFrom != null) {
            query.put(QUERY_ISSUED_FROM, issuedFrom.toString());
        }
        if (issuedTo != null) {
            query.put(QUERY_ISSUED_TO, issuedTo.toString());
        }

        JsonNode root = codec.readTree(httpRuntime.getRaw(path, query));
        JsonNode reports = root.get(FIELD_REPORTS);
        if (reports == null || !reports.isArray()) {
            return List.of();
        }
        List<DailyReport> parsed = new ArrayList<>();
        for (JsonNode report : reports) {
            parsed.add(DailyReportMapper.fromJson(report));
        }
        return List.copyOf(parsed);
    }

    private static Instant crkLastConnectedAt(JsonNode root) {
        JsonNode crkStatus = root.get(FIELD_CRK_STATUS);
        return crkStatus == null || !crkStatus.isObject()
                ? null
                : instant(crkStatus, FIELD_LAST_CONNECTED_AT);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException unparseable) {
            return null;
        }
    }
}
