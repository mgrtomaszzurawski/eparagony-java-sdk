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
package io.github.mgrtomaszzurawski.eparagony.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.mgrtomaszzurawski.eparagony.EparagonyClient;
import io.github.mgrtomaszzurawski.eparagony.core.auth.ClientCredentials;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.retry.RetryPolicy;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyAuthException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyRateLimitException;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;
import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.IdempotencyKey;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentEntry;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.PaymentForm;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptLine;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.ReceiptRequest;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.model.TaxRateCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hardening that has no happy path.
 *
 * <p>These checks exist for inputs a well-behaved server and a well-behaved caller never produce, so
 * nothing else in the suite exercises them — which is exactly how a defensive check rots into a
 * comment. Each test here pins one.
 */
class DefensiveInputTest {

    private static final String TOKEN_PATH = "/auth/token";
    private static final String DOCUMENT_TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final String STATUS_PATH = "/documents/" + DOCUMENT_TOKEN + "/status";
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final String DOCUMENTS_PATH = "/documents";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR = 503;
    private static final Duration BACKOFF = Duration.ofSeconds(30);
    private static final long AWAIT_SECONDS = 10L;

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        WireMock.configureFor("localhost", server.port());
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":3600,\"scope\":\"document_create\"}")));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    @DisplayName("refuses an idempotency key carrying a line break")
    void refusesIdempotencyKeyWithLineBreak() {
        // It is sent as a header. Left unchecked the JDK rejects it at the moment of the call, by
        // which point the caller is holding a key they believe is usable and a sale they think is
        // idempotent.
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> IdempotencyKey.of("key-1\r\nX-Injected: yes"));

        assertTrue(failure.getMessage().contains("header"),
                "the message must say why, but said: " + failure.getMessage());
        assertThrows(IllegalArgumentException.class, () -> IdempotencyKey.of("key-1\nsecond"));
    }

    @Test
    @DisplayName("refuses a token lifetime inside the cache's own expiry margin")
    void refusesNonPositiveTokenLifetime() {
        // 30 seconds is positive, so a "> 0" check would let it through — and AccessToken treats
        // anything within its 60-second margin as spent, so the cache would mint a new token per
        // request. That is the documented way to earn a 429 from this API, arriving as a mystery.
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":30,\"scope\":\"document_create\"}")));

        Documents documents = client().documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        EparagonyAuthException failure =
                assertThrows(EparagonyAuthException.class, () -> documents.status(token));

        assertTrue(failure.getMessage().contains("expires_in"),
                "the message must name the field, but said: " + failure.getMessage());
    }

    @Test
    @DisplayName("refuses a token lifetime that would overflow the expiry instant")
    void refusesImplausibleTokenLifetime() {
        // Long.MAX_VALUE seconds overflows Instant.plusSeconds, which would surface as a bare
        // ArithmeticException from outside the SDK's exception hierarchy.
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"opaque\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":" + Long.MAX_VALUE + ",\"scope\":\"document_create\"}")));

        Documents documents = client().documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        assertThrows(EparagonyAuthException.class, () -> documents.status(token));
    }

    @Test
    @DisplayName("bounds a malformed token echoed back from the server")
    void boundsAMalformedServerToken() {
        // The value rides on the CAUSE as well as the message, and log.error(msg, ex) prints the
        // cause verbatim — so the sanitizing has to happen where the exception is built, not only
        // where it is caught.
        String hostile = "not-a-uuid\r\n" + "z".repeat(5_000);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> DocumentToken.of(hostile));

        assertFalse(failure.getMessage().contains("\n"),
                "the cause must not carry a line break into a log");
        assertTrue(failure.getMessage().length() < hostile.length(),
                "the cause must not carry the whole value: " + failure.getMessage().length());
    }

    @Test
    @DisplayName("carries the server's Retry-After to the caller on a rate limit")
    void carriesRetryAfter() {
        stubRateLimited("30");

        Documents documents = client().documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        EparagonyRateLimitException failure =
                assertThrows(EparagonyRateLimitException.class, () -> documents.status(token));

        assertEquals(Duration.ofSeconds(30), failure.retryAfter().orElseThrow());
    }

    @Test
    @DisplayName("clamps an implausible Retry-After rather than dropping it")
    void clampsImplausibleRetryAfter() {
        // Long.MAX_VALUE seconds is a Duration whose toMillis() throws — and toMillis() is exactly
        // what a caller does with this value. Dropping it instead would be worse than clamping: the
        // SDK would report "no guidance" and retry sooner than a server asking to be left alone.
        stubRateLimited(String.valueOf(Long.MAX_VALUE));

        Documents documents = client().documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        EparagonyRateLimitException failure =
                assertThrows(EparagonyRateLimitException.class, () -> documents.status(token));

        Duration retryAfter = failure.retryAfter().orElseThrow();
        assertEquals(Duration.ofDays(1), retryAfter);
        assertEquals(Duration.ofDays(1).toMillis(), retryAfter.toMillis());
    }

    @Test
    @DisplayName("ignores a Retry-After that is not a number of seconds")
    void ignoresHttpDateRetryAfter() {
        // The HTTP-date form is legal and unparsed here; the computed backoff takes over rather than
        // the SDK inventing a wait from a date it did not read.
        stubRateLimited("Wed, 21 Oct 2026 07:28:00 GMT");

        Documents documents = client().documents();
        DocumentToken token = DocumentToken.of(DOCUMENT_TOKEN);

        EparagonyRateLimitException failure =
                assertThrows(EparagonyRateLimitException.class, () -> documents.status(token));

        assertTrue(failure.retryAfter().isEmpty());
    }

    @Test
    @DisplayName("reports a write as possibly applied when interrupted mid-backoff")
    void interruptedBackoffOnAWriteMayHaveBeenApplied() throws InterruptedException {
        // The CRITICAL this pins: sleepBackoff once hardcoded "not applied" for every caller. A write
        // interrupted while waiting to retry may already have reached the register, and telling the
        // caller otherwise invites them to reissue and fiscalize the sale twice. Review caught it;
        // nothing but this test keeps it caught.
        server.stubFor(post(urlPathEqualTo(DOCUMENTS_PATH)).willReturn(aResponse()
                .withStatus(HTTP_SERVER_ERROR)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"statusCode\":503,\"error\":\"Service Unavailable\"}")));

        Documents documents = retryingClient().documents();
        AtomicReference<EparagonyServerException> caught = new AtomicReference<>();
        AtomicBoolean interruptFlagRestored = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);

        Thread caller = new Thread(() -> {
            try {
                documents.issue(receipt());
            } catch (EparagonyServerException failed) {
                caught.set(failed);
                interruptFlagRestored.set(Thread.currentThread().isInterrupted());
            } finally {
                finished.countDown();
            }
        });
        caller.start();

        // Waiting for the request to appear in the journal is not enough: the response has not
        // necessarily reached the caller yet, so the interrupt could land inside httpClient.send()
        // and be handled by a different branch that asserts identically. Waiting until the thread is
        // parked in Thread.sleep — TIMED_WAITING — is what puts it demonstrably inside the backoff.
        awaitParkedInBackoff(caller);
        caller.interrupt();
        assertTrue(finished.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "an interrupted backoff must not leave the caller parked");

        EparagonyServerException failure = caught.get();
        assertNotNull(failure, "the interrupt must surface as an SDK exception, not escape as raw");
        // Names the backoff branch specifically. Without this the test would still pass if the
        // interrupt were caught while sending, which asserts the same flag for a different reason and
        // would leave the CRITICAL unguarded.
        assertTrue(failure.getMessage().contains("backing off"),
                "the interrupt must be the one in the retry backoff, but said: "
                        + failure.getMessage());
        assertTrue(failure.requestMayHaveBeenApplied(),
                "a POST interrupted mid-backoff may already have fiscalized; saying otherwise "
                        + "invites the caller to reissue and charge the customer twice");
        assertTrue(interruptFlagRestored.get(),
                "swallowing an InterruptedException must not swallow the interrupt itself");
    }

    /**
     * Spins until the worker is demonstrably parked in the retry backoff: the stub has seen the POST
     * <em>and</em> the thread has entered {@code TIMED_WAITING}, which on this path only
     * {@code Thread.sleep} in {@code sleepBackoff} produces.
     */
    private void awaitParkedInBackoff(Thread caller) {
        long deadline = System.nanoTime() + Duration.ofSeconds(AWAIT_SECONDS).toNanos();
        while (System.nanoTime() < deadline) {
            boolean posted = !server.findAll(postRequestedFor(urlPathEqualTo(DOCUMENTS_PATH)))
                    .isEmpty();
            if (posted && caller.getState() == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("the caller never parked in the retry backoff; it was "
                + caller.getState());
    }

    private void stubRateLimited(String retryAfter) {
        server.stubFor(get(urlPathEqualTo(STATUS_PATH)).willReturn(aResponse()
                .withStatus(HTTP_TOO_MANY_REQUESTS)
                .withHeader("Content-Type", "application/json")
                .withHeader(HEADER_RETRY_AFTER, retryAfter)
                .withBody("{\"statusCode\":429,\"error\":\"Too Many Requests\"}")));
    }

    /** A retrying client whose backoff is long enough to be interrupted inside deterministically. */
    private EparagonyClient retryingClient() {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .retryPolicy(RetryPolicy.builder()
                        .retryPost(true)
                        .initialBackoff(BACKOFF)
                        .maxBackoff(BACKOFF)
                        .build())
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .build());
    }

    private static ReceiptRequest receipt() {
        return ReceiptRequest.builder()
                .orderId("ORDER-INT")
                .addLine(ReceiptLine.builder()
                        .productOrServiceName("Karma")
                        .quantity(1)
                        .unitPrice(Amount.ofGrosze(1000))
                        .taxRate(TaxRateCode.A)
                        .build())
                .addPayment(PaymentEntry.of(PaymentForm.CASH, Amount.ofGrosze(1000)))
                .build();
    }

    private EparagonyClient client() {
        String baseUrl = "http://localhost:" + server.port();
        return EparagonyClient.of(EparagonyConfig.builder()
                .authBaseUrl(baseUrl)
                .apiBaseUrl(baseUrl)
                .credentials(new ClientCredentials("id", "secret"))
                .posId(PosId.of("pos"))
                .retryPolicy(RetryPolicy.disabled())
                .applicationUserAgent("TestApp/1.0 (+https://example.test)")
                .build());
    }
}
