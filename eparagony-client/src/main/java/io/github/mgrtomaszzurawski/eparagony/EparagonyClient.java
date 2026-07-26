package io.github.mgrtomaszzurawski.eparagony;

import io.github.mgrtomaszzurawski.eparagony.core.config.EparagonyConfig;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.WebhookSecret;
import io.github.mgrtomaszzurawski.eparagony.core.webhook.WebhookVerifier;
import io.github.mgrtomaszzurawski.eparagony.domain.documents.Documents;
import io.github.mgrtomaszzurawski.eparagony.domain.printers.Printers;
import io.github.mgrtomaszzurawski.eparagony.internal.ErrorMapper;
import io.github.mgrtomaszzurawski.eparagony.internal.HttpRuntime;
import io.github.mgrtomaszzurawski.eparagony.internal.JsonCodec;
import io.github.mgrtomaszzurawski.eparagony.internal.TokenManager;
import io.github.mgrtomaszzurawski.eparagony.internal.client.documents.DocumentsImpl;
import io.github.mgrtomaszzurawski.eparagony.internal.client.printers.PrintersImpl;

import java.net.http.HttpClient;
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

    /** Issuing documents and following their status. */
    public Documents documents() {
        ensureOpen();
        return documents;
    }

    /** Reading fiscal printer state. */
    public Printers printers() {
        ensureOpen();
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

    private static String userAgent(EparagonyConfig config) {
        return config.applicationUserAgent() + " " + SdkVersion.userAgentToken();
    }
}
