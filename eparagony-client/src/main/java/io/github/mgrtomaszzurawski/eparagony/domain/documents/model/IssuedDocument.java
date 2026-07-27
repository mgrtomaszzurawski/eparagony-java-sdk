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

import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;

import java.util.Objects;

/**
 * What eparagony.pl returns the moment a document is accepted — before the cash register has
 * fiscalized anything.
 *
 * <p>{@link #documentPublicUrl()} is live immediately, but do not send it to the customer yet: until
 * the status reaches {@code CONFIRMED} there may be no fiscal document behind it. Wait for the
 * webhook, or for {@code Documents.awaitTerminalStatus}.
 *
 * @param transactionToken identifies the commercial transaction
 * @param documentToken identifies this document. Equal to the transaction token when the caller
 *     supplied neither — the server mints one value and uses it for both.
 * @param documentPublicUrl the customer-facing visualization page
 * @param documentStatusUrl where this document's status can be polled
 * @param fiscalizationPending {@code true} when the server answered {@code 202} — data accepted,
 *     fiscalization still to come. {@code false} for {@code 200}, meaning nothing was ordered on the
 *     printer.
 */
public record IssuedDocument(
        TransactionToken transactionToken,
        DocumentToken documentToken,
        String documentPublicUrl,
        String documentStatusUrl,
        boolean fiscalizationPending) {

    public IssuedDocument {
        Objects.requireNonNull(transactionToken, "transactionToken");
        Objects.requireNonNull(documentToken, "documentToken");
        Objects.requireNonNull(documentPublicUrl, "documentPublicUrl");
        Objects.requireNonNull(documentStatusUrl, "documentStatusUrl");
    }
}
