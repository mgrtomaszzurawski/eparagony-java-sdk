package io.github.mgrtomaszzurawski.eparagony.core.retry;

/** How the wait between retries grows. */
public enum BackoffStrategy {

    /** Doubles each attempt, capped by {@code RetryPolicy.maxBackoff}. The sensible default. */
    EXPONENTIAL,

    /** The same wait every attempt. For callers with a hard, externally imposed cadence. */
    FIXED
}
