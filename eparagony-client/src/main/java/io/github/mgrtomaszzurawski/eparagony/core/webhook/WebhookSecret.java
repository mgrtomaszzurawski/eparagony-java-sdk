package io.github.mgrtomaszzurawski.eparagony.core.webhook;

import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;

import java.util.Objects;

/**
 * The shared secret eparagony.pl issues for signing webhook notifications. Distinct from the client
 * credentials and used for nothing else.
 *
 * <p>{@link #toString()} is redacted: this value is the only thing standing between a forged HTTP
 * request and your accounting.
 */
public record WebhookSecret(String value) {

    public WebhookSecret {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new EparagonyConfigurationException("webhookSecret must not be blank");
        }
    }

    public static WebhookSecret of(String value) {
        return new WebhookSecret(value);
    }

    @Override
    public String toString() {
        return "WebhookSecret[<redacted>]";
    }
}
