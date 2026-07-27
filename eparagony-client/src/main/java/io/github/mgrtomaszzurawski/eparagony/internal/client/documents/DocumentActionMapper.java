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
package io.github.mgrtomaszzurawski.eparagony.internal.client.documents;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.ActionStatusNotification;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ActionState;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ActionType;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentAction;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonReader;

/** Reads an action status notification. Internal: never exported. */
public final class DocumentActionMapper {

    private static final String FIELD_DOCUMENT_TOKEN = "documentToken";
    private static final String FIELD_ACTION_ID = "actionId";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_STATUS = "status";

    private DocumentActionMapper() {
    }

    /** Parses the body of a notification delivered to {@code actionStatusUrl}. */
    public static ActionStatusNotification fromJson(JsonNode root) {
        String documentToken = JsonReader.text(root, FIELD_DOCUMENT_TOKEN);
        return new ActionStatusNotification(
                documentToken == null ? null : DocumentToken.of(documentToken),
                new DocumentAction(
                        JsonReader.text(root, FIELD_ACTION_ID),
                        ActionType.fromWireValue(JsonReader.text(root, FIELD_TYPE)),
                        ActionState.fromWireValue(JsonReader.text(root, FIELD_STATUS))));
    }
}
