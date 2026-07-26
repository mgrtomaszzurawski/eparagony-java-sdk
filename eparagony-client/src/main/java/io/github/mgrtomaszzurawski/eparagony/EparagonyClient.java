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
package io.github.mgrtomaszzurawski.eparagony;

import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.WebhookNotifications;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.WebhookSecret;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.WebhookVerifier;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.Printers;
import io.github.mgrtomaszzurawski.eparagony.internal.ErrorMapper;
import io.github.mgrtomaszzurawski.eparagony.internal.HttpRuntime;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonCodec;
import io.github.mgrtomaszzurawski.eparagony.internal.TokenManager;
import io.github.mgrtomaszzurawski.eparagony.internal.client.documents.DocumentStatusMapper;
import io.github.mgrtomaszzurawski.eparagony.internal.client.documents.DocumentsImpl;
import io.github.mgrtomaszzurawski.eparagony.internal.client.printers.PrintersImpl;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;

/**
 * The entry point. Build one per application, keep it, and share it — it is thread-safe, it pools
 * connections, and it caches the access token that the API would otherwise throttle you for
 * re-minting.
 *
 * <pre>{@code
 * try (EparagonyClient client = EparagonyClient.of(EparagonyConfig.builder()
 *         .environment(Environment.SANDBOX)
 *         .credentials(new ClientCredentials(clientId, clientSecret))
 *         .posId(PosId.of("my-shop"))
 *         .applicationUserAgent("MyShop/1.0 (+https://myshop.example)")
 *         .build())) {
 *
 *     IssuedDocument issued = client.documents().issue(receipt);
 *     DocumentStatus status = client.documents()
 *             .awaitTerminalStatus(issued.documentToken(), Duration.ofMinutes(3));
 * }
 * }</pre>
 *
 * <p>Authentication is lazy: constructing a client performs no network call, so a bad credential
 * surfaces on the first document rather than at startup. Configuration errors, by contrast, are
 * raised immediately by {@link EparagonyConfig}.
 */
public final class EparagonyClient implements AutoCloseable {

    private final EparagonyConfig config;
    private final Documents documents;
    private final Printers printers;

    private volatile boolean closed;

    private EparagonyClient(EparagonyConfig config, Clock clock) {
        this.config = config;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JsonCodec codec = new JsonCodec();
        String userAgent = userAgent(config);
        TokenManager tokenManager = new TokenManager(httpClient, config, userAgent, codec, clock);
        HttpRuntime httpRuntime = new HttpRuntime(
                httpClient, config, userAgent, tokenManager, codec, new ErrorMapper(codec));
        this.documents = new DocumentsImpl(httpRuntime, codec, config.posId(), clock);
        this.printers = new PrintersImpl(httpRuntime, codec);
    }

    /** Builds a client from a configuration. */
    public static EparagonyClient of(EparagonyConfig config) {
        return of(config, Clock.systemUTC());
    }

    /** Builds a client against a supplied clock. Intended for tests that control time. */
    public static EparagonyClient of(EparagonyConfig config, Clock clock) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(clock, "clock");
        return new EparagonyClient(config, clock);
    }

    /**
     * Issuing documents and following their status. Requires {@link Scope#DOCUMENT_CREATE}.
     *
     * @throws EparagonyConfigurationException if that scope was not requested
     */
    public Documents documents() {
        ensureOpen();
        ensureScope(Scope.DOCUMENT_CREATE, "documents()");
        return documents;
    }

    /**
     * Reading fiscal printer state. Requires {@link Scope#PRINTER_GET}.
     *
     * @throws EparagonyConfigurationException if that scope was not requested
     */
    public Printers printers() {
        ensureOpen();
        ensureScope(Scope.PRINTER_GET, "printers()");
        return printers;
    }

    /**
     * A verifier for inbound webhook notifications.
     *
     * <p>Deliberately built from a secret passed in here rather than carried on
     * {@link EparagonyConfig}: verifying webhooks is a wholly separate concern from calling the API,
     * it needs a different secret, and the process that receives notifications is frequently not the
     * one that issues documents. Nothing forces you to hold API credentials to verify a signature.
     */
    public static WebhookVerifier webhookVerifier(WebhookSecret secret) {
        return new WebhookVerifier(secret);
    }

    /**
     * Verification and parsing of inbound webhook notifications, in one step.
     *
     * <p>Static, and built from the webhook secret alone, for the same reason as
     * {@link #webhookVerifier(WebhookSecret)}: the process receiving notifications is frequently not
     * the one issuing documents, and nothing should require API credentials to read a signed callback.
     *
     * <p>Prefer this over {@link #webhookVerifier(WebhookSecret)} — it makes it impossible to parse a
     * payload whose signature was never checked.
     */
    public static WebhookNotifications webhookNotifications(WebhookSecret secret) {
        JsonCodec codec = new JsonCodec();
        return new WebhookNotifications(new WebhookVerifier(secret),
                rawBody -> DocumentStatusMapper.fromJson(
                        codec.readTree(new String(rawBody, StandardCharsets.UTF_8))));
    }

    /** The configuration this client was built from. */
    public EparagonyConfig config() {
        return config;
    }

    /**
     * Releases the client. Further use throws {@link IllegalStateException}.
     *
     * <p>The underlying {@code HttpClient} has no {@code close()} on Java 17 — that arrived in Java 21
     * — so its connections are reclaimed by the garbage collector. This method exists so that
     * {@code try}-with-resources reads correctly today and keeps working when the baseline moves.
     */
    @Override
    public void close() {
        closed = true;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("EparagonyClient has been closed");
        }
    }

    /**
     * Refuses a facade whose scope was never requested.
     *
     * <p>Without this the SDK would happily hand back a facade backed by a token that cannot reach its
     * endpoints, and the consumer would meet the opaque {@code 403 Access denied} that ADR-003 exists
     * to eliminate — having already been protected from the same failure one layer up, at the token.
     * Checking the requested set is enough: the token manager separately refuses a token the server
     * did not actually grant.
     */
    private void ensureScope(Scope required, String accessor) {
        if (!config.scopes().contains(required)) {
            throw new EparagonyConfigurationException(accessor + " requires the "
                    + required.wireValue() + " scope, but this client was configured with "
                    + Scope.toWireValue(config.scopes())
                    + ". Add it to EparagonyConfig.scopes() — and make sure eparagony.pl has granted "
                    + "it to your client, or the token request itself will be rejected.");
        }
    }

    private static String userAgent(EparagonyConfig config) {
        return config.applicationUserAgent() + " " + SdkVersion.userAgentToken();
    }
}
