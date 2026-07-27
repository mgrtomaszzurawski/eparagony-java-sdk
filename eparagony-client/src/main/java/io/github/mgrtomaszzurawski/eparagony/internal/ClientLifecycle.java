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
package io.github.mgrtomaszzurawski.eparagony.internal;

/**
 * Whether the client that owns a facade is still open. Internal: never exported.
 *
 * <p>Shared between the client and the transport so that {@code close()} means what its Javadoc says.
 * Guarding only the accessors would leave a facade captured beforehand fully functional — still
 * issuing real fiscal documents from a client the application believes it has shut down.
 */
public final class ClientLifecycle {

    private volatile boolean closed;

    /** Marks the owning client closed. Idempotent. */
    public void close() {
        closed = true;
    }

    /**
     * Refuses to proceed once the owning client is closed.
     *
     * @throws IllegalStateException if the owning client has been closed
     */
    public void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("EparagonyClient has been closed");
        }
    }
}
