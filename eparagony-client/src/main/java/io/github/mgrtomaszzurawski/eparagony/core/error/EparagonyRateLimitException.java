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

import java.time.Duration;
import java.util.Optional;

/**
 * The server is throttling this client ({@code HTTP 429}).
 *
 * <p>Its own type because the remediation is unlike any other failure: nothing about the request is
 * wrong, and the fix is to slow down rather than to change anything. The API documents one specific
 * way to earn this — minting an access token per request instead of reusing it until expiry — and the
 * SDK's token cache exists to prevent exactly that, so seeing this usually means either a fleet
 * larger than the account's quota or a second client sharing the credentials.
 *
 * <p>Carries {@link #retryAfter()} when the server said how long to wait. The SDK's own
 * {@code RetryPolicy} already honours that header as a floor, so this exception surfaces only after
 * the configured attempts are exhausted.
 */
public final class EparagonyRateLimitException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    private final Duration retryAfter;

    public EparagonyRateLimitException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    /** How long the server asked the caller to wait, when it said. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
