package io.github.mgrtomaszzurawski.eparagony.core.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Verifies the {@code X-Signature} header eparagony.pl sends with every webhook notification: an
 * HMAC-SHA256 of the request body, keyed with the webhook secret, hex-encoded.
 *
 * <p><strong>This class takes raw bytes, and that is the entire point.</strong> The signature covers
 * the exact octets the server transmitted. Parsing the body to JSON and re-serializing it to compute
 * the digest is the single most common way to get this wrong — key order and whitespace shift, the
 * digest changes, and every legitimate notification starts failing verification. The API's own
 * documentation calls this out as a frequently asked question. There is deliberately no overload
 * taking a {@code String} or a parsed object: capture the body before your framework touches it.
 *
 * <p>It has no dependency on any HTTP framework either — no servlet, no Spring, nothing. Hand it a
 * {@code byte[]} and a header value from whatever is receiving your requests. That keeps the same
 * code working in a servlet filter, a Lambda handler, and a unit test.
 *
 * <p>Thread-safe: a fresh {@link Mac} is created per call, since {@code Mac} instances are not.
 */
public final class WebhookVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_HEX_LENGTH = 64;

    private final byte[] secretKey;

    public WebhookVerifier(WebhookSecret secret) {
        Objects.requireNonNull(secret, "secret");
        this.secretKey = secret.value().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verifies a notification, throwing when it does not match.
     *
     * @param rawBody the request body exactly as received, before any parsing
     * @param signatureHeader the {@code X-Signature} header value
     * @throws WebhookSignatureException when the header is absent, malformed, or does not match
     */
    public void verify(byte[] rawBody, String signatureHeader) {
        Objects.requireNonNull(rawBody, "rawBody");
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new WebhookSignatureException("Webhook notification carried no X-Signature header");
        }
        String presented = signatureHeader.trim();
        if (presented.length() != SIGNATURE_HEX_LENGTH) {
            throw new WebhookSignatureException("X-Signature must be " + SIGNATURE_HEX_LENGTH
                    + " hex characters (HMAC-SHA256) but was " + presented.length());
        }
        byte[] presentedDigest;
        try {
            presentedDigest = HexFormat.of().parseHex(presented.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException notHex) {
            throw new WebhookSignatureException("X-Signature is not valid hexadecimal");
        }
        byte[] expectedDigest = digest(rawBody);
        // Constant-time comparison: a byte-by-byte early exit leaks, through timing, how much of a
        // guessed signature was correct, which is enough to forge one given patience.
        if (!MessageDigest.isEqual(expectedDigest, presentedDigest)) {
            throw new WebhookSignatureException(
                    "X-Signature does not match the request body. Verify over the RAW bytes as "
                            + "received — a re-serialized JSON body produces a different digest.");
        }
    }

    /** Verifies without throwing, for callers that would only catch and branch anyway. */
    public boolean isValid(byte[] rawBody, String signatureHeader) {
        try {
            verify(rawBody, signatureHeader);
            return true;
        } catch (WebhookSignatureException mismatch) {
            return false;
        }
    }

    private byte[] digest(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            return mac.doFinal(rawBody);
        } catch (GeneralSecurityException unavailable) {
            // HmacSHA256 is mandated by the JDK; reaching this means a broken security provider.
            throw new IllegalStateException("HMAC-SHA256 is unavailable in this JVM", unavailable);
        }
    }
}
