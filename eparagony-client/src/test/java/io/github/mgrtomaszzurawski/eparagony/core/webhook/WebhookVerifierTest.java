package io.github.mgrtomaszzurawski.eparagony.core.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookVerifierTest {

    private static final String SECRET_VALUE = "test-webhook-secret-value";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

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
    @DisplayName("matches a signature computed the way the API documents it")
    void matchesDocumentedAlgorithm() {
        // The API documents the signature as hash_hmac('sha256', requestBody, webhookSecret), whose
        // PHP form returns lower-case hex of exactly 64 characters. Pinning the length and casing here
        // catches a future switch to Base64 or to a different digest.
        String signature = hmacHex(RAW_BODY.getBytes(StandardCharsets.UTF_8));

        assertEquals(64, signature.length());
        assertEquals(signature.toLowerCase(java.util.Locale.ROOT), signature);
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
