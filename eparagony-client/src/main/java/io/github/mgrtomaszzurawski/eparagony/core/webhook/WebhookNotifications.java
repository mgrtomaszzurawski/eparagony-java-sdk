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
import java.util.function.Function;

/**
 * Verifies an inbound webhook notification and parses it in one step.
 *
 * <p>The two operations are deliberately not separable. A consumer offered {@code verify} and
 * {@code parse} as independent calls can forget the first, and the resulting handler processes forged
 * fiscal notifications while looking entirely reasonable in review. Here the parsed result is only
 * reachable through the verification.
 *
 * <p>Like {@link WebhookVerifier}, this depends on no HTTP framework and no API credentials — a
 * {@code byte[]} and a header value are the whole input, so the same code works in a servlet filter,
 * a Lambda handler and a test.
 *
 * <pre>{@code
 * WebhookNotifications notifications = EparagonyClient.webhookNotifications(secret);
 *
 * DocumentStatusNotification notification =
 *         notifications.documentStatus(rawBodyBytes, request.getHeader("X-Signature"));
 *
 * if (notification.status().isConfirmed()) {
 *     emailReceiptLink(notification.status().documentUrl().orElseThrow());
 * }
 * }</pre>
 *
 * <p><strong>A signature proves origin, not freshness.</strong> Nothing in the notification carries a
 * timestamp or a nonce, so a captured request replays perfectly. Handlers must be idempotent —
 * deduplicate on {@code documentToken}, or on {@code actionId} for action notifications — and must
 * treat a repeat as the same event rather than a second one.
 */
public final class WebhookNotifications {

    private final WebhookVerifier verifier;
    private final Function<byte[], DocumentStatus> parser;
    private final Function<byte[], ActionStatusNotification> actionParser;

    /**
     * Assembled by {@code EparagonyClient.webhookNotifications(WebhookSecret)}, which is how you should
     * obtain one. Public only because the parser lives in the SDK's internal layer and {@code core}
     * must not depend on it — the dependency is injected here rather than inverted.
     *
     * <p>Supplying your own parser cannot weaken the guarantee: verification happens in
     * {@link #documentStatus} before the parser is ever consulted.
     */
    public WebhookNotifications(WebhookVerifier verifier, Function<byte[], DocumentStatus> parser,
            Function<byte[], ActionStatusNotification> actionParser) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.actionParser = Objects.requireNonNull(actionParser, "actionParser");
    }

    /**
     * Verifies and parses a document status notification.
     *
     * @param rawBody the request body exactly as received, before any parsing. Re-serialized JSON
     *     produces a different digest and will be rejected — see {@link WebhookVerifier}.
     * @param signatureHeader the {@code X-Signature} header value
     * @throws WebhookSignatureException if the signature does not match; do not process the payload
     */
    public DocumentStatusNotification documentStatus(byte[] rawBody, String signatureHeader) {
        verifier.verify(rawBody, signatureHeader);
        return new DocumentStatusNotification(parser.apply(rawBody));
    }

    /**
     * Verifies and parses an action status notification — the one delivered to {@code actionStatusUrl},
     * which is a different callback from the fiscalization one.
     *
     * <p>Both callbacks are signed with the same secret, so a handler serving one URL for both must
     * decide which parse to run from the URL it was reached on, not from the payload.
     *
     * @throws WebhookSignatureException if the signature does not match; do not process the payload
     */
    public ActionStatusNotification actionStatus(byte[] rawBody, String signatureHeader) {
        verifier.verify(rawBody, signatureHeader);
        return actionParser.apply(rawBody);
    }
}
