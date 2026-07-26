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
package io.github.mgrtomaszzurawski.eparagony.core.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    private static final int JITTER_SAMPLES = 200;

    @Test
    @DisplayName("does not retry writes unless asked to")
    void doesNotRetryWritesByDefault() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertTrue(policy.isRetryableStatus(500, true), "a read may be retried");
        assertFalse(policy.isRetryableStatus(500, false), "a write may not, by default");
        assertFalse(policy.isRetryableTransportFailure(false));
    }

    @Test
    @DisplayName("retries writes once opted in")
    void retriesWritesWhenEnabled() {
        RetryPolicy policy = RetryPolicy.builder().retryPost(true).build();

        assertTrue(policy.isRetryableStatus(500, false));
        assertTrue(policy.isRetryableTransportFailure(false));
    }

    @Test
    @DisplayName("retries 429 and 5xx but not 4xx")
    void retriesOnlyTransientStatuses() {
        RetryPolicy policy = RetryPolicy.defaults();

        assertTrue(policy.isRetryableStatus(429, true));
        assertTrue(policy.isRetryableStatus(503, true));
        assertFalse(policy.isRetryableStatus(400, true));
        assertFalse(policy.isRetryableStatus(403, true));
        assertFalse(policy.isRetryableStatus(404, true));
    }

    @Test
    @DisplayName("collapses to a single attempt when disabled")
    void disabledMeansOneAttempt() {
        assertEquals(1, RetryPolicy.disabled().maxAttempts());
        assertFalse(RetryPolicy.disabled().isRetryableStatus(500, true));
    }

    @RepeatedTest(value = JITTER_SAMPLES, name = "equal jitter stays within [base/2, base]")
    void jitterStaysWithinEqualJitterBand() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(1000))
                .maxBackoff(Duration.ofSeconds(60))
                .build();

        long firstRetry = policy.backoff(0, null).toMillis();
        long secondRetry = policy.backoff(1, null).toMillis();

        // Equal jitter: never zero (a thundering herd's opposite failure mode is a busy loop) and
        // never longer than the computed base.
        assertTrue(firstRetry >= 500 && firstRetry <= 1000, "first retry was " + firstRetry + "ms");
        assertTrue(secondRetry >= 1000 && secondRetry <= 2000, "second retry was " + secondRetry + "ms");
    }

    @Test
    @DisplayName("caps exponential growth at maxBackoff")
    void capsExponentialGrowth() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(500))
                .maxBackoff(Duration.ofSeconds(2))
                .build();

        assertTrue(policy.backoff(20, null).toMillis() <= 2000);
        // A pathological retry index must not overflow the shift into a negative wait.
        assertTrue(policy.backoff(Integer.MAX_VALUE, null).toMillis() > 0);
    }

    @RepeatedTest(value = JITTER_SAMPLES, name = "Retry-After is a floor that jitter never undercuts")
    void retryAfterIsAFloor() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(200))
                .build();

        long wait = policy.backoff(0, Duration.ofSeconds(5)).toMillis();

        // The computed backoff here is far shorter than what the server asked for. Honouring
        // Retry-After means waiting at least as long as asked, every single time.
        assertTrue(wait >= 5000, "waited " + wait + "ms, less than the server's Retry-After");
    }

    @Test
    @DisplayName("caps how long a Retry-After can park the thread")
    void capsRetryAfter() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetryAfter(Duration.ofSeconds(30))
                .build();

        assertTrue(policy.backoff(0, Duration.ofHours(2)).toMillis() <= 30_000);
    }

    @Test
    @DisplayName("uses a constant wait under the fixed strategy")
    void fixedStrategyDoesNotGrow() {
        RetryPolicy policy = RetryPolicy.builder()
                .backoffStrategy(BackoffStrategy.FIXED)
                .initialBackoff(Duration.ofMillis(1000))
                .maxBackoff(Duration.ofMillis(1000))
                .build();

        assertTrue(policy.backoff(5, null).toMillis() <= 1000);
    }

    @Test
    @DisplayName("rejects a nonsensical configuration at build time")
    void rejectsBadConfiguration() {
        assertThrows(RuntimeException.class, () -> RetryPolicy.builder().maxAttempts(0).build());
        assertThrows(RuntimeException.class,
                () -> RetryPolicy.builder().initialBackoff(Duration.ZERO).build());
        assertThrows(RuntimeException.class, () -> RetryPolicy.builder()
                .initialBackoff(Duration.ofSeconds(10))
                .maxBackoff(Duration.ofSeconds(1))
                .build());
    }
}
