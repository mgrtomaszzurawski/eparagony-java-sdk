package io.github.mgrtomaszzurawski.eparagony.core.retry;

import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * When and how the SDK retries a failed request. Immutable; build one with {@link #builder()} or take
 * {@link #defaults()}.
 *
 * <p>Two decisions here are deliberate and worth knowing about.
 *
 * <p><strong>Writes are not retried by default.</strong> {@code retryPost} defaults to {@code false}
 * because issuing a fiscal document is not idempotent from the caller's perspective unless they
 * manage the {@code Idempotency-Key} themselves. A retried {@code POST /documents} under a fresh key
 * fiscalizes the sale twice — a real accounting incident, not a duplicate row. The SDK reuses one key
 * across a retried call, which makes enabling this safe, but it stays opt-in.
 *
 * <p><strong>Backoff uses equal jitter, and {@code Retry-After} is a floor.</strong> The actual wait
 * is drawn from {@code [base/2, base]}, so a fleet of clients that all fail at the same instant does
 * not return in lockstep. When the server sends {@code Retry-After}, jitter may lengthen the wait but
 * never shortens it below what the server asked for.
 */
public final class RetryPolicy {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(20);
    private static final Duration DEFAULT_MAX_RETRY_AFTER = Duration.ofSeconds(120);

    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_MIN = 500;

    /** Guards the exponential shift against overflow on a pathological attempt count. */
    private static final int MAX_BACKOFF_SHIFT = 30;

    private static final int EQUAL_JITTER_DIVISOR = 2;

    private final boolean enabled;
    private final int maxAttempts;
    private final BackoffStrategy backoffStrategy;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final boolean retryOnServerError;
    private final boolean retryOnRateLimit;
    private final boolean retryPost;
    private final Duration maxRetryAfter;

    private RetryPolicy(Builder builder) {
        this.enabled = builder.enabled;
        this.maxAttempts = builder.maxAttempts;
        this.backoffStrategy = builder.backoffStrategy;
        this.initialBackoff = builder.initialBackoff;
        this.maxBackoff = builder.maxBackoff;
        this.retryOnServerError = builder.retryOnServerError;
        this.retryOnRateLimit = builder.retryOnRateLimit;
        this.retryPost = builder.retryPost;
        this.maxRetryAfter = builder.maxRetryAfter;
    }

    /** Three attempts, exponential equal-jitter backoff, retries 429 and 5xx, does not retry writes. */
    public static RetryPolicy defaults() {
        return builder().build();
    }

    /** Disables retrying entirely; every failure surfaces on the first attempt. */
    public static RetryPolicy disabled() {
        return builder().enabled(false).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean enabled() {
        return enabled;
    }

    /** Total attempts including the first, so {@code 3} means one call plus two retries. */
    public int maxAttempts() {
        return enabled ? maxAttempts : 1;
    }

    public BackoffStrategy backoffStrategy() {
        return backoffStrategy;
    }

    public boolean retryPost() {
        return retryPost;
    }

    /** {@code true} when a response with this status should be retried. */
    public boolean isRetryableStatus(int statusCode, boolean idempotent) {
        if (!enabled || !(idempotent || retryPost)) {
            return false;
        }
        if (statusCode == HTTP_TOO_MANY_REQUESTS) {
            return retryOnRateLimit;
        }
        return statusCode >= HTTP_SERVER_ERROR_MIN && retryOnServerError;
    }

    /** {@code true} when a connection failure or timeout should be retried. */
    public boolean isRetryableTransportFailure(boolean idempotent) {
        return enabled && (idempotent || retryPost);
    }

    /**
     * The wait before retry number {@code retryIndex} (zero-based), with equal jitter applied.
     *
     * @param retryAfterFloor the server's {@code Retry-After}, or {@code null} when it sent none;
     *     capped at {@code maxRetryAfter} and never undercut by jitter
     */
    public Duration backoff(int retryIndex, Duration retryAfterFloor) {
        Duration base = backoffStrategy == BackoffStrategy.FIXED
                ? initialBackoff
                : exponentialBase(retryIndex);
        Duration jittered = applyEqualJitter(base);
        if (retryAfterFloor == null) {
            return jittered;
        }
        Duration cappedFloor = retryAfterFloor.compareTo(maxRetryAfter) > 0 ? maxRetryAfter : retryAfterFloor;
        return jittered.compareTo(cappedFloor) < 0 ? cappedFloor : jittered;
    }

    private Duration exponentialBase(int retryIndex) {
        int shift = Math.min(Math.max(retryIndex, 0), MAX_BACKOFF_SHIFT);
        long scaled = initialBackoff.toMillis() << shift;
        return scaled >= maxBackoff.toMillis() || scaled < 0
                ? maxBackoff
                : Duration.ofMillis(scaled);
    }

    private static Duration applyEqualJitter(Duration base) {
        long half = base.toMillis() / EQUAL_JITTER_DIVISOR;
        if (half <= 0) {
            return base;
        }
        // Drawn from [half, base] — never zero, never longer than the computed base.
        return Duration.ofMillis(half + ThreadLocalRandom.current().nextLong(half + 1));
    }

    /** Builder for {@link RetryPolicy}. */
    public static final class Builder {

        private boolean enabled = true;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private BackoffStrategy backoffStrategy = BackoffStrategy.EXPONENTIAL;
        private Duration initialBackoff = DEFAULT_INITIAL_BACKOFF;
        private Duration maxBackoff = DEFAULT_MAX_BACKOFF;
        private boolean retryOnServerError = true;
        private boolean retryOnRateLimit = true;
        private boolean retryPost;
        private Duration maxRetryAfter = DEFAULT_MAX_RETRY_AFTER;

        private Builder() {
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
            return this;
        }

        public Builder maxAttempts(int value) {
            if (value < 1) {
                throw new EparagonyConfigurationException("maxAttempts must be at least 1");
            }
            this.maxAttempts = value;
            return this;
        }

        public Builder backoffStrategy(BackoffStrategy value) {
            this.backoffStrategy = Objects.requireNonNull(value, "backoffStrategy");
            return this;
        }

        public Builder initialBackoff(Duration value) {
            this.initialBackoff = requirePositive(value, "initialBackoff");
            return this;
        }

        public Builder maxBackoff(Duration value) {
            this.maxBackoff = requirePositive(value, "maxBackoff");
            return this;
        }

        public Builder retryOnServerError(boolean value) {
            this.retryOnServerError = value;
            return this;
        }

        public Builder retryOnRateLimit(boolean value) {
            this.retryOnRateLimit = value;
            return this;
        }

        /**
         * Enables retrying non-idempotent writes. Safe with the SDK's own retry loop, which reuses one
         * {@code Idempotency-Key} across attempts of a single call, but off by default — see the class
         * documentation for why a double-fiscalized sale is worth being conservative about.
         */
        public Builder retryPost(boolean value) {
            this.retryPost = value;
            return this;
        }

        /** Caps how long a server-supplied {@code Retry-After} can park the calling thread. */
        public Builder maxRetryAfter(Duration value) {
            this.maxRetryAfter = requirePositive(value, "maxRetryAfter");
            return this;
        }

        public RetryPolicy build() {
            if (maxBackoff.compareTo(initialBackoff) < 0) {
                throw new EparagonyConfigurationException("maxBackoff must not be shorter than initialBackoff");
            }
            return new RetryPolicy(this);
        }

        private static Duration requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isNegative() || value.isZero()) {
                throw new EparagonyConfigurationException(name + " must be positive");
            }
            return value;
        }
    }
}
