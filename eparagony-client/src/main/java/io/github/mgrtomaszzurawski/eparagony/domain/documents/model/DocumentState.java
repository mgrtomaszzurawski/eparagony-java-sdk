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
 * Where a document has got to.
 *
 * <p><strong>The polling endpoint and the webhook do not emit the same set of states</strong>, and
 * assuming they do is a real source of bugs. Each constant below records which channel produces it.
 * An unrecognised value from either channel maps to {@link #UNKNOWN} rather than failing: the API's
 * integration guide warns that the contract may grow without notice, and a status the SDK has not
 * seen before is not a reason to drop a fiscal notification on the floor.
 */
public enum DocumentState {

    /** Accepted, fiscalization in progress. <strong>Polling only</strong> — never sent as a webhook. */
    PENDING(false),

    /**
     * The printer processed the commands and the paper receipt printed correctly.
     * <strong>Webhook only</strong>, and only when {@code print: true} was requested. Note that the
     * document may not have reached the repository yet at this point.
     */
    READY(false),

    /**
     * The document is in the repository — for an invoice submitted to KSeF, with a KSeF number
     * assigned. This is the state at which it is safe to show the customer their receipt. Emitted by
     * both channels.
     */
    CONFIRMED(true),

    /**
     * Issued in KSeF offline mode, because processing took longer than a few seconds. A visualization
     * with two QR codes is available. Emitted by both channels; applies to invoices, not receipts.
     */
    OFFLINE(true),

    /** Issuance failed permanently. Emitted by both channels. */
    ERROR(true),

    /** A state this SDK does not recognise. Treat as non-terminal and keep waiting. */
    UNKNOWN(false);

    private final boolean terminal;

    DocumentState(boolean terminal) {
        this.terminal = terminal;
    }

    /** {@code true} when no further transition is expected and waiting should stop. */
    public boolean isTerminal() {
        return terminal;
    }

    /** Maps a wire value, never throwing — see {@link #UNKNOWN}. */
    public static DocumentState fromWireValue(String wireValue) {
        if (wireValue == null) {
            return UNKNOWN;
        }
        for (DocumentState state : values()) {
            if (state != UNKNOWN && state.name().equalsIgnoreCase(wireValue.trim())) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
