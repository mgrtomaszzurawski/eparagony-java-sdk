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

import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.DocumentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public webhook path: verify and parse, with no way to reach the second without the first.
 *
 * <p>Also the only place {@link DocumentState#READY} is observable — the polling endpoint never emits
 * it, so without a webhook parser that state was unreachable through any public API.
 */
class WebhookNotificationsTest {

    private static final String SECRET = "webhook-secret";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WebhookNotifications notifications =
            EparagonyClient.webhookNotifications(WebhookSecret.of(SECRET));

    @Test
    @DisplayName("verifies and parses a CONFIRMED notification")
    void parsesConfirmed() {
        String body = """
                {"status":"CONFIRMED","documentType":"RECEIPT","processingMode":"FISCALIZATION",
                 "documentToken":"11111111-2222-4333-8444-555555555555",
                 "fiscalDeviceUniqueNumber":"ZBN1901007833","fiscalDocumentId":"ZBN1901007833/570",
                 "receiptNumber":210,"printed":false,
                 "documentUrl":"https://hub.example/view/abc"}
                """;

        DocumentStatusNotification notification =
                notifications.documentStatus(bytes(body), signature(body));

        assertEquals(DocumentState.CONFIRMED, notification.status().state());
        assertTrue(notification.status().isConfirmed());
        assertEquals("ZBN1901007833/570", notification.status().fiscalDocumentId().orElseThrow());
        assertEquals(false, notification.status().printed().orElseThrow());
        assertEquals("https://hub.example/view/abc", notification.status().documentUrl().orElseThrow());
    }

    @Test
    @DisplayName("parses READY, which the polling endpoint never emits")
    void parsesReady() {
        String body = "{\"status\":\"READY\",\"printed\":true}";

        DocumentStatusNotification notification =
                notifications.documentStatus(bytes(body), signature(body));

        assertEquals(DocumentState.READY, notification.status().state());
        // READY means the paper printed; the document may not have reached the repository yet.
        assertTrue(notification.status().printed().orElseThrow());
        assertTrue(!notification.status().isTerminal(), "READY is not a terminal state");
    }

    @Test
    @DisplayName("parses ERROR with the server's failure detail")
    void parsesError() {
        String body = "{\"status\":\"ERROR\",\"errorMessage\":\"schodek podatkowy\"}";

        DocumentStatusNotification notification =
                notifications.documentStatus(bytes(body), signature(body));

        assertEquals(DocumentState.ERROR, notification.status().state());
        assertEquals("schodek podatkowy", notification.status().errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("refuses to parse a notification whose signature does not match")
    void refusesUnverifiedNotification() {
        String body = "{\"status\":\"CONFIRMED\"}";
        String foreignSignature = signature("{\"status\":\"ERROR\"}");

        assertThrows(WebhookSignatureException.class,
                () -> notifications.documentStatus(bytes(body), foreignSignature));
    }

    @Test
    @DisplayName("refuses a notification carrying no signature at all")
    void refusesUnsignedNotification() {
        String body = "{\"status\":\"CONFIRMED\"}";

        assertThrows(WebhookSignatureException.class,
                () -> notifications.documentStatus(bytes(body), null));
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static String signature(String body) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(hmac.doFinal(bytes(body)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
