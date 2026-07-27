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
package io.github.mgrtomaszzurawski.eparagony.core.config;

import io.github.mgrtomaszzurawski.eparagony.core.auth.Credentials;
import io.github.mgrtomaszzurawski.eparagony.core.auth.Scope;
import io.github.mgrtomaszzurawski.eparagony.core.error.EparagonyConfigurationException;
import io.github.mgrtomaszzurawski.eparagony.core.model.PosId;
import io.github.mgrtomaszzurawski.eparagony.core.retry.RetryPolicy;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
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

    private static final String SCHEME_HTTPS = "https";
    private static final String SCHEME_HTTP = "http";

    /** Hosts for which cleartext HTTP is tolerated, because nothing leaves the machine. */
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    /** Characters that would end a header line and begin another one. */
    private static final String HEADER_LINE_BREAKS = "\r\n";

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
        // An EnumSet, not Set.copyOf: the latter randomizes iteration order per JVM run, which would
        // make the `scope` parameter of the token request differ between runs of the same program.
        // The server accepts any order, but a request body that is not reproducible is a poor thing to
        // debug, log, or pin a test against.
        this.scopes = Collections.unmodifiableSet(EnumSet.copyOf(builder.scopes));
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

        /**
         * Overrides the authorization base URL. Intended for tests pointing at a local stub server.
         *
         * <p>Plain HTTP is permitted only against a loopback address. Everything the SDK sends to this
         * host is a credential, so an unencrypted override to a real hostname would put the client
         * secret on the wire in cleartext — a misconfiguration worth refusing rather than warning
         * about.
         */
        public Builder authBaseUrl(String value) {
            requireSecureOrLoopback(value, "authBaseUrl");
            this.authBaseUrl = value;
            return this;
        }

        /**
         * Overrides the API base URL. Intended for tests pointing at a local stub server. Subject to
         * the same transport rule as {@link #authBaseUrl(String)}: bearer tokens travel here.
         */
        public Builder apiBaseUrl(String value) {
            requireSecureOrLoopback(value, "apiBaseUrl");
            this.apiBaseUrl = value;
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
            // Collected into an EnumSet directly rather than through Set.of, which rejects a repeated
            // element with IllegalArgumentException. Asking for the same scope twice is redundant, not
            // an error.
            EnumSet<Scope> requested = EnumSet.noneOf(Scope.class);
            requested.addAll(java.util.Arrays.asList(values));
            this.scopes = requested;
            return this;
        }

        /**
         * Identifies the calling application, e.g. {@code "MyShop/2.1 (+https://myshop.example)"}. The
         * API requires a non-generic User-Agent and rejects the default one an HTTP stack would send,
         * so this has no sensible default and must be supplied.
         */
        public Builder applicationUserAgent(String value) {
            this.applicationUserAgent = requireHeaderSafe(value, "applicationUserAgent");
            return this;
        }

        /** Sets {@code X-Integration-Id}, required of integrators serving multiple merchants. */
        public Builder integrationId(String value) {
            this.integrationId = requireHeaderSafe(value, "integrationId");
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

        /**
         * Accepts {@code https://} anywhere, and {@code http://} only against loopback. Both base URLs
         * carry secrets — the client secret to one, the bearer token to the other — so cleartext to a
         * remote host is refused outright.
         */
        private static void requireSecureOrLoopback(String value, String name) {
            Objects.requireNonNull(value, name);
            URI parsed = parseUrl(value, name);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            boolean secure = SCHEME_HTTPS.equals(scheme)
                    || SCHEME_HTTP.equals(scheme) && isLoopback(parsed.getHost());
            if (!secure) {
                throw new EparagonyConfigurationException(name + " must use https, or http against a "
                        + "loopback address for testing, but was: " + value);
            }
        }

        private static URI parseUrl(String value, String name) {
            try {
                return URI.create(value);
            } catch (IllegalArgumentException malformed) {
                throw new EparagonyConfigurationException(
                        name + " is not a valid URL: " + value, malformed);
            }
        }

        /**
         * Rejects a value that would break out of the header it is written into.
         *
         * <p>Both of these end up in an HTTP header. The JDK's own client refuses a CR or LF too, but
         * it refuses it at send time, deep in a stack trace and long after the misconfiguration was
         * introduced. This class exists to fail at construction, and a promise to validate
         * configuration should not have an exception for the values that carry injection risk.
         */
        private static String requireHeaderSafe(String value, String name) {
            Objects.requireNonNull(value, name);
            for (int index = 0; index < value.length(); index++) {
                if (HEADER_LINE_BREAKS.indexOf(value.charAt(index)) >= 0) {
                    throw new EparagonyConfigurationException(
                            name + " must not contain a carriage return or line feed; it is sent as an "
                                    + "HTTP header");
                }
            }
            return value;
        }

        private static boolean isLoopback(String host) {
            return host != null && LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT));
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
                        "applicationUserAgent must identify your application, e.g. \"MyShop/2.1 (+https://myshop.example)\"");
            }
            String leadingToken = trimmed.split("[/ ]", 2)[0].toLowerCase(Locale.ROOT);
            if (GENERIC_USER_AGENT_PREFIXES.contains(leadingToken)) {
                throw new EparagonyConfigurationException(
                        "applicationUserAgent \"" + trimmed + "\" is generic and the API rejects it; "
                                + "identify your application, e.g. \"MyShop/2.1 (+https://myshop.example)\"");
            }
        }
    }
}
