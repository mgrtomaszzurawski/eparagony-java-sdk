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

import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentStatus;

import java.util.Objects;

/**
 * A verified fiscalization notification.
 *
 * <p>Obtaining one is the only way through {@link WebhookNotifications}, which means a
 * {@code DocumentStatusNotification} in hand is proof the signature checked out. There is deliberately
 * no constructor that skips verification — an unverified notification and a verified one being the
 * same type is how a handler ends up trusting a forged request.
 *
 * <p>Note the status set differs from the polling endpoint's: a webhook can report
 * {@link io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentState#READY}, which
 * polling never emits, and never reports {@code PENDING}.
 */
public record DocumentStatusNotification(DocumentStatus status) {

    public DocumentStatusNotification {
        Objects.requireNonNull(status, "status");
    }
}
