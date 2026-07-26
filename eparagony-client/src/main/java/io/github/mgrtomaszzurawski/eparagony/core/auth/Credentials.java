package io.github.mgrtomaszzurawski.eparagony.core.auth;

/**
 * How the SDK authenticates against eparagony.pl. Sealed: the API publishes exactly one OAuth 2 flow
 * (client credentials), and the token request body is a discriminated union on {@code grant_type}
 * with only {@code client_credentials} mapped. Keeping the type sealed rather than collapsing it to
 * a single class leaves room for a second grant without a breaking change, while making it
 * impossible for a consumer to invent a third.
 */
public sealed interface Credentials permits ClientCredentials {

    /** The OAuth 2 {@code grant_type} this credential performs. */
    String grantType();
}
