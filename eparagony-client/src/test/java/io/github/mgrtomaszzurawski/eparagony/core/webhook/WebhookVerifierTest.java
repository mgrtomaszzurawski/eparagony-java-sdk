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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookVerifierTest {

    private static final String SECRET_VALUE = "test-webhook-secret-value";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** HMAC-SHA256 of the ASCII bytes "eparagony" under the key "key", hex-encoded. */
    private static final String KNOWN_ANSWER_SIGNATURE =
            "b6627987af0233976e7bdaf0e02617a5e2f2edb8d1f886213dd782d660dab91e";

    /**
     * A body whose re-serialization would differ from its raw form: the key order and the spacing are
     * both things a JSON library would normalize away. This is what makes the raw-bytes contract
     * testable rather than merely asserted in prose.
     */
    private static final String RAW_BODY =
            "{\"status\":\"CONFIRMED\",  \"documentToken\":\"abc\",\"receiptNumber\":210}";

    private static final String REORDERED_BODY =
            "{\"documentToken\":\"abc\",\"receiptNumber\":210,\"status\":\"CONFIRMED\"}";

    private final WebhookVerifier verifier = new WebhookVerifier(WebhookSecret.of(SECRET_VALUE));

    @Test
    @DisplayName("accepts a signature computed over the exact bytes received")
    void acceptsMatchingSignature() {
        byte[] body = RAW_BODY.getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> verifier.verify(body, hmacHex(body)));
        assertTrue(verifier.isValid(body, hmacHex(body)));
    }

    @Test
    @DisplayName("accepts an upper-case hex signature")
    void acceptsUpperCaseHex() {
        byte[] body = RAW_BODY.getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> verifier.verify(body, hmacHex(body).toUpperCase(java.util.Locale.ROOT)));
    }

    @Test
    @DisplayName("rejects a body that was re-serialized rather than passed through raw")
    void rejectsReserializedBody() {
        // The signature the server computed, over the bytes the server sent.
        String signature = hmacHex(RAW_BODY.getBytes(StandardCharsets.UTF_8));
        // What a handler that parsed and re-serialized the JSON would hand the verifier instead.
        byte[] reserialized = REORDERED_BODY.getBytes(StandardCharsets.UTF_8);

        WebhookSignatureException failure =
                assertThrows(WebhookSignatureException.class, () -> verifier.verify(reserialized, signature));
        assertTrue(failure.getMessage().contains("RAW bytes"),
                "the failure must point at the re-serialization trap, but said: " + failure.getMessage());
    }

    @Test
    @DisplayName("rejects a body altered by a single byte")
    void rejectsTamperedBody() {
        byte[] original = RAW_BODY.getBytes(StandardCharsets.UTF_8);
        String signature = hmacHex(original);
        byte[] tampered = RAW_BODY.replace("210", "211").getBytes(StandardCharsets.UTF_8);

        assertThrows(WebhookSignatureException.class, () -> verifier.verify(tampered, signature));
        assertFalse(verifier.isValid(tampered, signature));
    }

    @Test
    @DisplayName("rejects a signature produced with a different secret")
    void rejectsForeignSecret() {
        byte[] body = RAW_BODY.getBytes(StandardCharsets.UTF_8);
        String forged = hmacHex(body, "some-other-secret");

        assertThrows(WebhookSignatureException.class, () -> verifier.verify(body, forged));
    }

    @Test
    @DisplayName("rejects an absent, blank, short or non-hex signature header")
    void rejectsMalformedHeader() {
        byte[] body = RAW_BODY.getBytes(StandardCharsets.UTF_8);

        assertThrows(WebhookSignatureException.class, () -> verifier.verify(body, null));
        assertThrows(WebhookSignatureException.class, () -> verifier.verify(body, "  "));
        assertThrows(WebhookSignatureException.class, () -> verifier.verify(body, "abc123"));
        assertThrows(WebhookSignatureException.class,
                () -> verifier.verify(body, "z".repeat(64)));
    }

    @Test
    @DisplayName("verifies an empty body rather than treating it as a special case")
    void verifiesEmptyBody() {
        byte[] empty = new byte[0];

        assertDoesNotThrow(() -> verifier.verify(empty, hmacHex(empty)));
    }

    @Test
    @DisplayName("keeps the secret out of toString")
    void redactsSecret() {
        assertFalse(WebhookSecret.of(SECRET_VALUE).toString().contains(SECRET_VALUE));
    }

    @Test
    @DisplayName("accepts a known-answer vector rather than a signature it computed itself")
    void acceptsKnownAnswerVector() {
        // A fixed vector, independently reproducible with:
        //   printf '%s' 'eparagony' | openssl dgst -sha256 -hmac 'key' -hex
        //
        // The previous version of this test asserted that its own helper returned 64 lower-case hex
        // characters — true by construction, and it never invoked the verifier at all. This one
        // fails if the algorithm, the key encoding or the output encoding ever changes.
        WebhookVerifier keyedVerifier = new WebhookVerifier(WebhookSecret.of("key"));
        byte[] body = "eparagony".getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> keyedVerifier.verify(body, KNOWN_ANSWER_SIGNATURE));
        assertFalse(keyedVerifier.isValid(body, KNOWN_ANSWER_SIGNATURE.replace('b', 'c')),
                "a digest that is not the known answer must be rejected");
    }

    private static String hmacHex(byte[] body) {
        return hmacHex(body, SECRET_VALUE);
    }

    private static String hmacHex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
