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
import java.util.Optional;

/**
 * Asks eparagony.pl to deliver the issued receipt to Allegro, attached to a marketplace order.
 *
 * <p>Attached to a document at issuance. The delivery runs asynchronously afterwards, reports through
 * {@code documents().actions()} and, if an {@link #actionStatusUrl()} is given, fires its own webhook
 * — a separate one per action, distinct from the fiscalization notification.
 *
 * @param orderId the Allegro order the receipt belongs to
 * @param actionId a caller-chosen identifier for this action. Each action's webhook carries it, so
 *     without one a consumer running several actions cannot tell which notification is which.
 * @param accountId the seller's Allegro account, when they have more than one
 * @param accountName that account's name, for readability in status reports
 * @param actionStatusUrl where to POST this action's status notification
 */
public record AllegroDelivery(String orderId, String actionId, String accountId, String accountName,
        String actionStatusUrl) {

    public AllegroDelivery {
        Objects.requireNonNull(orderId, "orderId");
        if (orderId.isBlank()) {
            throw new IllegalArgumentException("the Allegro orderId must not be blank");
        }
    }

    /** Delivery to the seller's only Allegro account. */
    public static AllegroDelivery forOrder(String orderId) {
        return new AllegroDelivery(orderId, null, null, null, null);
    }

    /**
     * The same delivery, tagged so its webhook can be correlated. Supply one whenever a document
     * carries more than one action.
     */
    public AllegroDelivery identifiedAs(String value) {
        return new AllegroDelivery(orderId, Objects.requireNonNull(value, "actionId"), accountId,
                accountName, actionStatusUrl);
    }

    public Optional<String> actionIdIfPresent() {
        return Optional.ofNullable(actionId);
    }

    /** The same delivery, naming which Allegro account the order belongs to. */
    public AllegroDelivery onAccount(String accountId, String accountName) {
        return new AllegroDelivery(orderId, actionId, Objects.requireNonNull(accountId, "accountId"),
                Objects.requireNonNull(accountName, "accountName"), actionStatusUrl);
    }

    /** The same delivery, with its own status webhook. */
    public AllegroDelivery notifyingAt(String url) {
        return new AllegroDelivery(orderId, actionId, accountId, accountName,
                Objects.requireNonNull(url, "actionStatusUrl"));
    }

    public Optional<String> accountIdIfPresent() {
        return Optional.ofNullable(accountId);
    }

    public Optional<String> accountNameIfPresent() {
        return Optional.ofNullable(accountName);
    }

    public Optional<String> actionStatusUrlIfPresent() {
        return Optional.ofNullable(actionStatusUrl);
    }
}
