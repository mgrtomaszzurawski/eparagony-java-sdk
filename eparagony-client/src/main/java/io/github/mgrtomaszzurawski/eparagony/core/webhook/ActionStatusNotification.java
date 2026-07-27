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
package io.github.mgrtomaszzurawski.eparagony.core.webhook;

import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentAction;

import java.util.Objects;
import java.util.Optional;

/**
 * A verified notification that one of a document's asynchronous actions finished.
 *
 * <p>A separate notification per action, which is why {@link DocumentAction#actionId()} matters: a
 * document running several actions produces several of these, and the identifier is the only thing
 * telling them apart.
 *
 * <p>The action channel never reports {@code PENDING} — it fires only on the terminal outcomes,
 * {@code COMPLETED} and {@code FAILED}. Poll {@code documents().actions(...)} if you need the
 * in-progress state.
 *
 * @param documentToken the document the action belongs to, when the server named it
 * @param action the action and its outcome
 */
public record ActionStatusNotification(DocumentToken documentToken, DocumentAction action) {

    public ActionStatusNotification {
        Objects.requireNonNull(action, "action");
    }

    public Optional<DocumentToken> documentTokenIfPresent() {
        return Optional.ofNullable(documentToken);
    }
}
