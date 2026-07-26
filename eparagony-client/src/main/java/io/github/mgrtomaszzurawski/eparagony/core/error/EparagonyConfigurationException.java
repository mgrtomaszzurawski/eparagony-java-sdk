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
package io.github.mgrtomaszzurawski.eparagony.core.error;

/**
 * The SDK was assembled wrongly — a missing credential, a blank {@code posId}, a malformed base URL.
 * Raised eagerly at construction time, never mid-call, so a misconfiguration fails on the first line
 * of application startup rather than on the first document.
 *
 * <p>Remediation: fix the configuration and restart. Retrying cannot help.
 */
public final class EparagonyConfigurationException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public EparagonyConfigurationException(String message) {
        super(message);
    }
}
