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
 * Base type for every failure the SDK raises. Subtypes are grouped by <em>what the caller can do
 * about it</em>, not by HTTP status: catching a subtype tells you the remediation, catching this
 * type tells you only that the call did not succeed.
 *
 * <p>Unchecked by design — a fiscal document is issued from application code that already has an
 * error path, and checked exceptions on every call would be noise.
 */
public class EparagonyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EparagonyException(String message) {
        super(message);
    }

    public EparagonyException(String message, Throwable cause) {
        super(message, cause);
    }
}
