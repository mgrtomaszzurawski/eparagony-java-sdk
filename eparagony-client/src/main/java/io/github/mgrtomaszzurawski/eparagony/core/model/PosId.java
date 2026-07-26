package io.github.mgrtomaszzurawski.eparagony.core.model;

import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;

import java.util.Objects;

/**
 * Identifies the point of sale — a physical till or an e-commerce storefront — and is the identifier
 * every party to the transaction shares. Issued by eparagony.pl alongside the client credentials.
 *
 * <p>A distinct type rather than a {@code String} because it travels next to several other opaque
 * string identifiers, and transposing it with one of them produces a {@code 403} whose message names
 * none of them.
 */
public record PosId(String value) {

    public PosId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new EparagonyConfigurationException("posId must not be blank");
        }
    }

    /** Wraps the identifier issued by eparagony.pl, e.g. {@code "sklepzoologicznybarkshop"}. */
    public static PosId of(String value) {
        return new PosId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
