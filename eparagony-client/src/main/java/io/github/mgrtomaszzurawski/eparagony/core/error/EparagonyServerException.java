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
 * The call did not complete for a reason on the far side of the wire: a {@code 5xx} response, a
 * network failure, or a timeout. These are one type because the remediation is one thing — back off
 * and try again later, and escalate if it persists. Splitting them would ask the caller to write
 * three catch blocks with the same body.
 *
 * <p>What the caller <em>does</em> need to distinguish is whether the request may already have taken
 * effect, which is why {@link #requestMayHaveBeenApplied()} exists. A timeout on
 * {@code POST /documents} is the dangerous case: the fiscal document may well have been issued, and
 * blindly reissuing it would double-fiscalize a sale. Resend under the <em>same</em>
 * {@code Idempotency-Key} — that is precisely what the key is for — or query the status first.
 */
public final class EparagonyServerException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    // NOT transient. Both are part of the exception's contract: `requestMayHaveBeenApplied` is how a
    // caller decides whether reissuing a receipt would double-fiscalize a sale. Marking them transient
    // on a Serializable type would silently reset them to 0/false across a round trip — turning
    // "the sale may already be fiscalized" into "it definitely is not".
    private final int statusCode;
    private final boolean requestMayHaveBeenApplied;

    /** Sentinel {@link #statusCode()} for a failure that never produced an HTTP response. */
    public static final int NO_HTTP_RESPONSE = 0;

    public EparagonyServerException(String message, int statusCode, boolean requestMayHaveBeenApplied) {
        super(message);
        this.statusCode = statusCode;
        this.requestMayHaveBeenApplied = requestMayHaveBeenApplied;
    }

    public EparagonyServerException(String message, Throwable cause, boolean requestMayHaveBeenApplied) {
        super(message, cause);
        this.statusCode = NO_HTTP_RESPONSE;
        this.requestMayHaveBeenApplied = requestMayHaveBeenApplied;
    }

    /** The HTTP status, or {@link #NO_HTTP_RESPONSE} when the failure was a network error or timeout. */
    public int statusCode() {
        return statusCode;
    }

    /**
     * {@code true} when the request was a non-idempotent write that may have been applied server-side
     * despite the failure. Reissue only under the original {@code Idempotency-Key}.
     */
    public boolean requestMayHaveBeenApplied() {
        return requestMayHaveBeenApplied;
    }
}
