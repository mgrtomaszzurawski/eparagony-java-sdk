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
 * The server accepted the token but refused the operation ({@code HTTP 403}). Distinct from
 * {@link EparagonyAuthException} because the remediation is different: the credential itself is
 * fine, so what needs checking is <em>which</em> resource was addressed and with what authority.
 *
 * <p>Observed causes, in order of likelihood: the token was minted for a scope that does not cover
 * this endpoint; the {@code posId} does not belong to the authenticated client; or the addressed
 * {@code documentToken} belongs to another client. Note the last one — the API answers 403, not 404,
 * for a document it will not show you, so a "not found" and a "not yours" are indistinguishable from
 * the outside.
 */
public final class EparagonyAccessDeniedException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public EparagonyAccessDeniedException(String message) {
        super(message);
    }
}
