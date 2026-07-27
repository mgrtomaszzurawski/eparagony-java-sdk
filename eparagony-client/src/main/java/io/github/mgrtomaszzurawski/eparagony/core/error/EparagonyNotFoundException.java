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
 * The addressed resource does not exist ({@code HTTP 404}) — a business outcome, not a system fault,
 * so it is worth catching on its own to distinguish "no such document" from "the call failed".
 *
 * <p>Remediation: check the identifier. Note that the API does <em>not</em> use 404 uniformly: a
 * document that exists but belongs to another client answers {@code 403}
 * ({@link EparagonyAccessDeniedException}), and a document issued under an older {@code X-Api-Version}
 * answers {@code 400} ({@link EparagonyValidationException}). Absence of this exception is therefore
 * not proof that a document exists.
 */
public final class EparagonyNotFoundException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public EparagonyNotFoundException(String message) {
        super(message);
    }
}
