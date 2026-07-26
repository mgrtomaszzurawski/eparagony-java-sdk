package io.github.mgrtomaszzurawski.eparagony.core.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a single issued document. A UUIDv4, either supplied by the caller or minted by the
 * server when the caller supplies none.
 *
 * <p>Do not assume it differs from the {@link TransactionToken}: when the caller supplies neither,
 * the server returns the same value for both. Do not assume it equals it either — the two are
 * independent concepts, and a retry after a fiscalization error deliberately reuses the transaction
 * token while taking a fresh document token.
 */
public record DocumentToken(String value) {

    public DocumentToken {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("documentToken must not be blank");
        }
    }

    public static DocumentToken of(String value) {
        return new DocumentToken(value);
    }

    /** Mints a fresh random token, for a caller that wants to know the identifier before issuing. */
    public static DocumentToken random() {
        return new DocumentToken(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
