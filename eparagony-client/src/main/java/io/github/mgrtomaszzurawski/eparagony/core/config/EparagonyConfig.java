package io.github.mgrtomaszzurawski.eparagony.core.config;

import io.github.mgrtomaszzurawski.eparagony.core.auth.Credentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.core.retry.RetryPolicy;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the SDK needs to talk to eparagony.pl. Immutable; build one with {@link #builder()} and
 * hand it to the client. Every constraint is checked here, at construction, so a misconfiguration
 * surfaces during application startup instead of on the first document of the day.
 */
public final class EparagonyConfig {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MINIMUM_APPLICATION_USER_AGENT_LENGTH = 3;

    /**
     * User-Agent values the API rejects as generic. Version 3.0 of the API made a non-generic
     * User-Agent mandatory, and these are exactly what a default HTTP stack sends.
     */
    private static final Set<String> GENERIC_USER_AGENT_PREFIXES =
            Set.of("java", "java-http-client", "okhttp", "apache-httpclient", "curl", "wget", "python");

    private final Environment environment;
    private final String authBaseUrl;
    private final String apiBaseUrl;
    private final Credentials credentials;
    private final PosId posId;
    private final Set<Scope> scopes;
    private final String applicationUserAgent;
    private final String integrationId;
    private final Duration requestTimeout;
    private final RetryPolicy retryPolicy;

    private EparagonyConfig(Builder builder) {
        this.environment = builder.environment;
        this.authBaseUrl = stripTrailingSlash(
                builder.authBaseUrl != null ? builder.authBaseUrl : builder.environment.authBaseUrl());
        this.apiBaseUrl = stripTrailingSlash(
                builder.apiBaseUrl != null ? builder.apiBaseUrl : builder.environment.apiBaseUrl());
        this.credentials = builder.credentials;
        this.posId = builder.posId;
        this.scopes = Set.copyOf(builder.scopes);
        this.applicationUserAgent = builder.applicationUserAgent;
        this.integrationId = builder.integrationId;
        this.requestTimeout = builder.requestTimeout;
        this.retryPolicy = builder.retryPolicy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Environment environment() {
        return environment;
    }

    /** Base URL of the authorization server. Distinct from {@link #apiBaseUrl()} — see {@link Environment}. */
    public String authBaseUrl() {
        return authBaseUrl;
    }

    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    public Credentials credentials() {
        return credentials;
    }

    public PosId posId() {
        return posId;
    }

    /** Scopes the SDK requests when minting a token. */
    public Set<Scope> scopes() {
        return scopes;
    }

    /** The consumer's own application identifier, which the SDK folds into the {@code User-Agent}. */
    public String applicationUserAgent() {
        return applicationUserAgent;
    }

    /** {@code X-Integration-Id}, issued by eparagony.pl to integrators serving multiple merchants. */
    public Optional<String> integrationId() {
        return Optional.ofNullable(integrationId);
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Builder for {@link EparagonyConfig}. */
    public static final class Builder {

        private Environment environment = Environment.SANDBOX;
        private String authBaseUrl;
        private String apiBaseUrl;
        private Credentials credentials;
        private PosId posId;
        private Set<Scope> scopes = EnumSet.of(Scope.DOCUMENT_CREATE);
        private String applicationUserAgent;
        private String integrationId;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private RetryPolicy retryPolicy = RetryPolicy.defaults();

        private Builder() {
        }

        /** Selects the deployment. Defaults to {@link Environment#SANDBOX} — production is opt-in. */
        public Builder environment(Environment value) {
            this.environment = Objects.requireNonNull(value, "environment");
            return this;
        }

        /** Overrides the authorization base URL. Intended for tests pointing at a local stub server. */
        public Builder authBaseUrl(String value) {
            this.authBaseUrl = Objects.requireNonNull(value, "authBaseUrl");
            return this;
        }

        /** Overrides the API base URL. Intended for tests pointing at a local stub server. */
        public Builder apiBaseUrl(String value) {
            this.apiBaseUrl = Objects.requireNonNull(value, "apiBaseUrl");
            return this;
        }

        public Builder credentials(Credentials value) {
            this.credentials = Objects.requireNonNull(value, "credentials");
            return this;
        }

        public Builder posId(PosId value) {
            this.posId = Objects.requireNonNull(value, "posId");
            return this;
        }

        /**
         * Scopes to request. Ask only for what the client is actually granted: the authorization
         * server rejects an un-granted scope outright, so a superset request fails the whole token.
         */
        public Builder scopes(Scope... values) {
            Objects.requireNonNull(values, "scopes");
            if (values.length == 0) {
                throw new EparagonyConfigurationException("at least one scope must be requested");
            }
            this.scopes = EnumSet.copyOf(Set.of(values));
            return this;
        }

        /**
         * Identifies the calling application, e.g. {@code "BarkShop/2.1 (+https://barkshop.pl)"}. The
         * API requires a non-generic User-Agent and rejects the default one an HTTP stack would send,
         * so this has no sensible default and must be supplied.
         */
        public Builder applicationUserAgent(String value) {
            this.applicationUserAgent = Objects.requireNonNull(value, "applicationUserAgent");
            return this;
        }

        /** Sets {@code X-Integration-Id}, required of integrators serving multiple merchants. */
        public Builder integrationId(String value) {
            this.integrationId = Objects.requireNonNull(value, "integrationId");
            return this;
        }

        public Builder requestTimeout(Duration value) {
            this.requestTimeout = Objects.requireNonNull(value, "requestTimeout");
            return this;
        }

        public Builder retryPolicy(RetryPolicy value) {
            this.retryPolicy = Objects.requireNonNull(value, "retryPolicy");
            return this;
        }

        public EparagonyConfig build() {
            requirePresent(credentials, "credentials");
            requirePresent(posId, "posId");
            requirePresent(applicationUserAgent, "applicationUserAgent");
            validateApplicationUserAgent(applicationUserAgent);
            if (requestTimeout.isNegative() || requestTimeout.isZero()) {
                throw new EparagonyConfigurationException("requestTimeout must be positive");
            }
            return new EparagonyConfig(this);
        }

        private static void requirePresent(Object value, String name) {
            if (value == null) {
                throw new EparagonyConfigurationException(name + " is required");
            }
        }

        private static void validateApplicationUserAgent(String value) {
            String trimmed = value.trim();
            if (trimmed.length() < MINIMUM_APPLICATION_USER_AGENT_LENGTH) {
                throw new EparagonyConfigurationException(
                        "applicationUserAgent must identify your application, e.g. \"BarkShop/2.1 (+https://barkshop.pl)\"");
            }
            String leadingToken = trimmed.split("[/ ]", 2)[0].toLowerCase(java.util.Locale.ROOT);
            if (GENERIC_USER_AGENT_PREFIXES.contains(leadingToken)) {
                throw new EparagonyConfigurationException(
                        "applicationUserAgent \"" + trimmed + "\" is generic and the API rejects it; "
                                + "identify your application, e.g. \"BarkShop/2.1 (+https://barkshop.pl)\"");
            }
        }
    }
}
