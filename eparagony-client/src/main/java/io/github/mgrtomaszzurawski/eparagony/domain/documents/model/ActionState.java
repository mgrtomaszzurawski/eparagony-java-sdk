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

/**
 * How far an asynchronous document action has got.
 *
 * <p>Like document status, the two channels differ: the action-status endpoint reports
 * {@link #PENDING}, and the webhook deliberately does not — it fires only on the terminal outcomes.
 */
public enum ActionState {

    /** Still running. Reported by the endpoint; <strong>never</strong> sent as a webhook. */
    PENDING(false),

    /** Finished successfully. */
    COMPLETED(true),

    /** Finished unsuccessfully. */
    FAILED(true),

    /** A state this SDK does not recognise. Treated as non-terminal. */
    UNKNOWN(false);

    private final boolean terminal;

    ActionState(boolean terminal) {
        this.terminal = terminal;
    }

    /** {@code true} when no further transition is expected. */
    public boolean isTerminal() {
        return terminal;
    }

    /** Maps a wire value, never throwing — see {@link #UNKNOWN}. */
    public static ActionState fromWireValue(String wireValue) {
        if (wireValue == null) {
            return UNKNOWN;
        }
        for (ActionState state : values()) {
            if (state != UNKNOWN && state.name().equalsIgnoreCase(wireValue.trim())) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
