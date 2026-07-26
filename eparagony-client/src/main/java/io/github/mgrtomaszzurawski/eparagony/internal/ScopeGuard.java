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

import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;

import java.util.Set;

/**
 * Refuses an operation whose scope the client never requested. Internal: never exported.
 *
 * <p>Per operation, not per facade, because scopes are not granted per facade: reading a document's
 * status needs {@code document_create}, its actions need {@code document_action_get}, and its JWS
 * needs {@code document_get_jws} — three different grants behind one {@code documents()} accessor.
 *
 * <p>The alternative is letting the call reach the server and come back as
 * {@code 403 Access denied}, which names neither the scope nor the operation. Refusing here costs a
 * set lookup and produces a message that says exactly what to do.
 */
public final class ScopeGuard {

    private final Set<Scope> configuredScopes;

    public ScopeGuard(Set<Scope> configuredScopes) {
        this.configuredScopes = Set.copyOf(configuredScopes);
    }

    /**
     * Checks one operation's scope.
     *
     * @param required the scope the endpoint's security block names
     * @param operation how to describe the call in the failure, e.g. {@code documents().actions()}
     */
    public void require(Scope required, String operation) {
        if (configuredScopes.contains(required)) {
            return;
        }
        throw new EparagonyConfigurationException(operation + " requires the " + required.wireValue()
                + " scope, but this client was configured with " + Scope.toWireValue(configuredScopes)
                + ". Add it to EparagonyConfig.scopes() — and make sure eparagony.pl has granted it to "
                + "your client, or the token request itself will be rejected with invalid_scope.");
    }
}
