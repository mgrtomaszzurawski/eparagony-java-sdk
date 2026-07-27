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

import io.github.mgrtomaszzurawski.eparagony.core.auth.AccessToken;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyServerException;
import io.github.mgrtomaszzurawski.eparagony.core.model.IdempotencyKey;
import io.github.mgrtomaszzurawski.eparagony.core.retry.RetryPolicy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The single transport chokepoint. Builds requests against the API base URL, attaches the bearer
 * token and the headers the API requires on every call, executes them, applies the
 * {@link RetryPolicy}, and hands non-2xx responses to {@link ErrorMapper}. Internal: never exported.
 *
 * <p>Deliberately narrow: the API is seven endpoints over two verbs, so this exposes GET and POST and
 * nothing else. There is no generic verb surface to misuse.
 */
public final class HttpRuntime {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_API_VERSION = "X-Api-Version";
    private static final String HEADER_INTEGRATION_ID = "X-Integration-Id";
    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final long MAX_PLAUSIBLE_RETRY_AFTER_SECONDS = Duration.ofDays(1).toSeconds();

    private static final String MEDIA_TYPE_JSON = "application/json";
    private static final String METHOD_POST = "POST";

    private static final int HTTP_OK_MIN = 200;
    private static final int HTTP_OK_MAX_EXCLUSIVE = 300;
    private static final int HTTP_UNAUTHORIZED = 401;

    private static final boolean IDEMPOTENT = true;
    private static final boolean NON_IDEMPOTENT = false;

    private static final int FIRST_RETRY_INDEX = 0;

    private final HttpClient httpClient;
    private final EparagonyConfig config;
    private final String userAgent;
    private final TokenManager tokenManager;
    private final JsonCodec codec;
    private final ErrorMapper errorMapper;

    private final ClientLifecycle lifecycle;

    public HttpRuntime(HttpClient httpClient, EparagonyConfig config, String userAgent,
            TokenManager tokenManager, JsonCodec codec, ErrorMapper errorMapper,
            ClientLifecycle lifecycle) {
        this.httpClient = httpClient;
        this.config = config;
        this.userAgent = userAgent;
        this.tokenManager = tokenManager;
        this.codec = codec;
        this.errorMapper = errorMapper;
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    /** {@code GET path}, decoding the JSON response into {@code responseType}. */
    public <T> T get(String path, Class<T> responseType) {
        return get(path, Map.of(), responseType);
    }

    /** {@code GET path} with query parameters, decoding the JSON response into {@code responseType}. */
    public <T> T get(String path, Map<String, String> queryParameters, Class<T> responseType) {
        return execute(path, IDEMPOTENT, null,
                () -> requestBuilder(path, queryParameters).GET().build(),
                response -> codec.read(response.body(), responseType));
    }

    /** {@code GET path}, returning the raw response body. For payloads the SDK resolves itself. */
    public String getRaw(String path) {
        return getRaw(path, Map.of());
    }

    /** {@code GET path} with query parameters, returning the raw response body. */
    public String getRaw(String path, Map<String, String> queryParameters) {
        return execute(path, IDEMPOTENT, null,
                () -> requestBuilder(path, queryParameters).GET().build(),
                RawResponse::body);
    }

    /**
     * {@code POST path} with a JSON body, returning status and body together.
     *
     * <p>The {@code Idempotency-Key} is fixed for the life of this call and reused across retries.
     * That is what makes retrying a document-issuing write safe at all: the server recognises the
     * repeat and does not fiscalize the sale a second time.
     */
    public RawResponse post(String path, Object requestBody, IdempotencyKey idempotencyKey) {
        String payload = codec.write(requestBody);
        return execute(path, NON_IDEMPOTENT, idempotencyKey,
                () -> requestBuilder(path, Map.of())
                        .header(HEADER_CONTENT_TYPE, MEDIA_TYPE_JSON)
                        .method(METHOD_POST,
                                HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build(),
                Function.identity());
    }

    private HttpRequest.Builder requestBuilder(String path, Map<String, String> queryParameters) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(config.apiBaseUrl() + path + QueryParameters.render(queryParameters)))
                .timeout(config.requestTimeout())
                .header(HEADER_ACCEPT, MEDIA_TYPE_JSON)
                .header(HEADER_USER_AGENT, userAgent)
                .header(HEADER_API_VERSION, ApiVersion.CURRENT);
        config.integrationId().ifPresent(value -> builder.header(HEADER_INTEGRATION_ID, value));
        return builder;
    }

    /**
     * Runs a request with retry and a single re-authentication.
     *
     * <p>The request is rebuilt per attempt so a refreshed bearer token can be attached; the
     * idempotency key, being fixed, keeps the retried write identical from the server's point of view.
     */
    private <T> T execute(String path, boolean idempotent, IdempotencyKey idempotencyKey,
            java.util.function.Supplier<HttpRequest> requestFactory, Function<RawResponse, T> decoder) {
        lifecycle.ensureOpen();
        int attempt = 0;
        int retryIndex = FIRST_RETRY_INDEX;
        boolean reauthenticated = false;
        RetryPolicy retryPolicy = config.retryPolicy();

        while (true) {
            attempt++;
            boolean mayRetry = attempt < retryPolicy.maxAttempts();
            AccessToken attemptToken = tokenManager.currentToken();
            HttpResponse<String> response = sendOrRetry(
                    requestFactory.get(), attemptToken, idempotencyKey, path, idempotent, mayRetry, retryIndex);
            if (response == null) {
                retryIndex++;
                continue;
            }

            int status = response.statusCode();
            if (isSuccess(status)) {
                return decoder.apply(new RawResponse(status, response.body()));
            }
            // Re-authenticate once and only once. A token can expire in the moment between the cache
            // check and the call reaching the server, which one retry fixes. Anything beyond that
            // means the credential itself is wrong, and repeatedly asking for a new token is exactly
            // the behaviour the authorization server throttles.
            if (status == HTTP_UNAUTHORIZED && !reauthenticated) {
                reauthenticated = true;
                // Compare-and-clear against the token this attempt actually used, so a concurrent
                // thread's freshly minted token is not thrown away with it.
                tokenManager.invalidate(attemptToken);
                continue;
            }
            if (mayRetry && retryPolicy.isRetryableStatus(status, idempotent)) {
                sleepBackoff(retryPolicy, retryIndex++, retryAfterFloor(response), idempotent);
                continue;
            }
            throw errorMapper.toException(status, response.body(), path, idempotent,
                    retryAfterFloor(response));
        }
    }

    /**
     * Sends one attempt. Returns the response, or {@code null} to mean "a retryable transport failure
     * happened and the backoff has already been served" — which keeps the retry bookkeeping out of
     * the caller's exception handling.
     */
    private HttpResponse<String> sendOrRetry(HttpRequest unauthorized, AccessToken token,
            IdempotencyKey idempotencyKey, String path, boolean idempotent, boolean mayRetry,
            int retryIndex) {
        RetryPolicy retryPolicy = config.retryPolicy();
        try {
            return httpClient.send(authorize(unauthorized, token, idempotencyKey),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            if (mayRetry && retryPolicy.isRetryableTransportFailure(idempotent)) {
                sleepBackoff(retryPolicy, retryIndex, null, idempotent);
                return null;
            }
            throw new EparagonyServerException("Request to " + path + " failed", failure, !idempotent);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EparagonyServerException("Request to " + path + " was interrupted", interrupted,
                    !idempotent);
        }
    }

    private HttpRequest authorize(HttpRequest request, AccessToken token, IdempotencyKey idempotencyKey) {
        HttpRequest.Builder authorized = HttpRequest.newBuilder(request, (name, value) -> true)
                .header(HEADER_AUTHORIZATION, token.authorizationHeaderValue());
        if (idempotencyKey != null) {
            authorized.header(HEADER_IDEMPOTENCY_KEY, idempotencyKey.value());
        }
        return authorized.build();
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= HTTP_OK_MIN && statusCode < HTTP_OK_MAX_EXCLUSIVE;
    }

    /**
     * Waits before the next attempt.
     *
     * <p>Takes {@code idempotent} because every path that reaches here has <em>already transmitted the
     * request</em> — this is the pause between a failed attempt and the next one. An interrupt during
     * that pause (an executor shutting down, a cancelled request) must therefore report the same
     * "may already have been applied" verdict as any other post-transmission failure. Reporting
     * {@code false} here told a caller their receipt definitely had not been issued, and the documented
     * remediation for that is to reissue under a fresh key — fiscalizing the sale twice.
     */
    private void sleepBackoff(RetryPolicy retryPolicy, int retryIndex, Duration retryAfterFloor,
            boolean idempotent) {
        Duration wait = retryPolicy.backoff(retryIndex, retryAfterFloor);
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EparagonyServerException("Interrupted while backing off before a retry",
                    interrupted, !idempotent);
        }
    }

    private static Duration retryAfterFloor(HttpResponse<String> response) {
        Optional<String> header = response.headers().firstValue(HEADER_RETRY_AFTER);
        if (header.isEmpty()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.get().trim());
            // Bounded, not just non-negative. This value is handed to the caller on
            // EparagonyRateLimitException, and the obvious thing to do with it is
            // Thread.sleep(retryAfter().toMillis()) — which throws ArithmeticException for a Duration
            // near Long.MAX_VALUE. A wait of more than a day is not an instruction anyone can act on,
            // so it is treated as unusable rather than propagated as a number that breaks arithmetic.
            return seconds >= 0 && seconds <= MAX_PLAUSIBLE_RETRY_AFTER_SECONDS
                    ? Duration.ofSeconds(seconds)
                    : null;
        } catch (NumberFormatException notAnInteger) {
            // The HTTP-date form is legal but not honored as a floor; fall back to computed backoff.
            return null;
        }
    }
}
