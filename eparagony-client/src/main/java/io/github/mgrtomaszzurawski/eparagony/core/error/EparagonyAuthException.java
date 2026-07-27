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
 * Authentication failed: the credentials were rejected, a requested scope is not granted to the
 * client, or the authorization server issued a token the SDK refuses to use.
 *
 * <p>Remediation: fix {@code clientId}/{@code clientSecret}, or have the missing scope granted by
 * eparagony.pl support. Retrying with the same inputs cannot help.
 *
 * <p>The last case deserves explanation, because the server makes it easy to get wrong. The token
 * endpoint answers {@code HTTP 200} even for a scope string it does not recognise, omitting the
 * {@code scope} field from the response and issuing a token that is rejected with an opaque
 * {@code 403 Access denied} by every endpoint. The SDK therefore treats a token response without a
 * granted {@code scope} as an authentication failure here, at the point of cause, rather than
 * letting the caller discover it later as an unexplainable 403.
 */
public final class EparagonyAuthException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public EparagonyAuthException(String message) {
        super(message);
    }

    public EparagonyAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
