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
package io.github.mgrtomaszzurawski.eparagony.domain.documents.model;

import java.util.Objects;

/**
 * One asynchronous action attached to a document, and where it has got to.
 *
 * @param actionId identifies this action; a document may carry several, and each webhook names one
 * @param type what the action does
 * @param state how far it has got
 */
public record DocumentAction(String actionId, ActionType type, ActionState state) {

    public DocumentAction {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
    }

    /** {@code true} when the action finished successfully. */
    public boolean isCompleted() {
        return state == ActionState.COMPLETED;
    }
}
