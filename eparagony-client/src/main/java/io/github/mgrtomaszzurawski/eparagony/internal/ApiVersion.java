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

/**
 * The {@code X-Api-Version} this SDK speaks. Internal: never exported.
 *
 * <p>Version 3 only, and that is a contract with consequences rather than a default. The version a
 * document is issued under governs how it can be read back: a document created under version 1
 * answers {@code 400} when its status is requested with {@code X-Api-Version: 3}. This SDK therefore
 * cannot read documents issued by an older integration against the same {@code posId}.
 */
public final class ApiVersion {

    /** The only version this SDK sends, on every request including the token call. */
    public static final String CURRENT = "3";

    private ApiVersion() {
    }
}
