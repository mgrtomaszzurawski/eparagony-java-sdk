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
package io.github.mgrtomaszzurawski.eparagony.core.webhook;

import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyException;

/**
 * A webhook notification did not carry a signature matching its body.
 *
 * <p>Remediation is unlike every other failure in this SDK, which is why this is its own type: do not
 * retry, do not repair, do not process the payload. Answer the caller with a client error and treat
 * the notification as untrusted. Either it was forged, or the body was altered in transit, or — the
 * overwhelmingly common cause in practice — the verifier was handed a re-serialized JSON string
 * rather than the raw bytes the server signed.
 */
public final class WebhookSignatureException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public WebhookSignatureException(String message) {
        super(message);
    }

    public WebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
