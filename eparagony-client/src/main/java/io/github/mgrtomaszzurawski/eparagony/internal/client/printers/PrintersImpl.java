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
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.Printers;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterState;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.model.PrinterStatus;
import io.github.mgrtomaszzurawski.eparagony.internal.ApiPaths;
import io.github.mgrtomaszzurawski.eparagony.internal.HttpRuntime;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonCodec;
import io.github.mgrtomaszzurawski.eparagony.internal.PathTemplate;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** Wires {@link Printers} onto the transport. Internal: never exported. */
public final class PrintersImpl implements Printers {

    private static final String PATH_PARAM_DEVICE = "fiscalDeviceUniqueNumber";

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_LAST_ACTIVE_AT = "lastActiveAt";
    private static final String FIELD_CRK_STATUS = "crkStatus";
    private static final String FIELD_LAST_CONNECTED_AT = "lastConnectedAt";

    private final HttpRuntime httpRuntime;
    private final JsonCodec codec;

    public PrintersImpl(HttpRuntime httpRuntime, JsonCodec codec) {
        this.httpRuntime = httpRuntime;
        this.codec = codec;
    }

    @Override
    public PrinterStatus status(FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber) {
        Objects.requireNonNull(fiscalDeviceUniqueNumber, "fiscalDeviceUniqueNumber");
        String path = PathTemplate.expand(
                ApiPaths.PRINTER_STATUS, PATH_PARAM_DEVICE, fiscalDeviceUniqueNumber.value());
        JsonNode root = codec.readTree(httpRuntime.getRaw(path));
        return new PrinterStatus(
                PrinterState.fromWireValue(text(root, FIELD_STATUS)),
                instant(root, FIELD_LAST_ACTIVE_AT),
                crkLastConnectedAt(root));
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
