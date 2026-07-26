package io.github.mgrtomaszzurawski.eparagony.core.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies the commercial transaction a document belongs to. A UUIDv4.
 *
 * <p>It outlives any single document. When fiscalization fails — the classic case being the Polish
 * <em>schodek podatkowy</em>, a tax-rate ordering violation the cash register rejects — the
 * documented recovery is to reissue under the <em>same</em> transaction token with a fresh
 * {@link DocumentToken}. The new fiscal document is then served from the visualization URL minted by
 * the first attempt.
 */
public record TransactionToken(String value) {

    public TransactionToken {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("transactionToken must not be blank");
        }
    }

    public static TransactionToken of(String value) {
        return new TransactionToken(value);
    }

    public static TransactionToken random() {
        return new TransactionToken(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
