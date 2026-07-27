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
package io.github.mgrtomaszzurawski.eparagony.internal;

/**
 * Every path the SDK addresses, in one place. The whole API is seven endpoints, so this is the
 * complete surface rather than a sample of it. Internal: never exported.
 */
public final class ApiPaths {

    /** {@code POST} — mints an access token. Lives on the auth host, not the API host. */
    public static final String AUTH_TOKEN = "/auth/token";

    /** {@code POST} — issues a document. */
    public static final String DOCUMENTS = "/documents";

    /** {@code GET} — current status of an issued document. */
    public static final String DOCUMENT_STATUS = "/documents/{documentToken}/status";

    /** {@code GET} — aggregate status of a document's asynchronous actions. */
    public static final String DOCUMENT_ACTIONS_STATUS = "/documents/{documentToken}/actions/status";

    /** {@code GET} — the signed JWS form of an issued document. */
    public static final String DOCUMENT_JWS = "/documents/{documentToken}/jws";

    /** {@code GET} — current status of a fiscal printer. */
    public static final String PRINTER_STATUS = "/printers/{fiscalDeviceUniqueNumber}/status";

    /** {@code GET} — daily fiscal reports for a printer. */
    public static final String PRINTER_DAILY_REPORTS = "/printers/{fiscalDeviceUniqueNumber}/reports/daily";

    private ApiPaths() {
    }
}
