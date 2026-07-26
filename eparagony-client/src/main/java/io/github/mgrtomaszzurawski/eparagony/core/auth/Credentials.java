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
