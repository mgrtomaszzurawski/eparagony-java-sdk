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

import java.util.Optional;

/**
 * The request was rejected as invalid or as a business-rule violation ({@code HTTP 400}). Most
 * commonly the amounts do not reconcile — the payments must cover the sold value, and every line's
 * total must equal unit price times quantity; eparagony.pl validates this before the document ever
 * reaches the cash register.
 *
 * <p>Remediation: correct the payload. Resending it unchanged will fail identically.
 *
 * <p>Carries the server's numeric {@code errorCode} when one was supplied. It is the only machine-
 * readable discriminator the API offers for this status, and it is not enumerated in the published
 * specification, so it is surfaced as a raw number rather than being mapped to an enum the SDK
 * would have to guess at.
 */
public final class EparagonyValidationException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    private final transient Integer errorCode;

    public EparagonyValidationException(String message, Integer errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /** The server's numeric error code, when the response carried one. */
    public Optional<Integer> errorCode() {
        return Optional.ofNullable(errorCode);
    }
}
