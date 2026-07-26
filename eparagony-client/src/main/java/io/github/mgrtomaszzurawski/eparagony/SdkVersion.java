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

import java.util.Optional;

/**
 * The SDK's own identity, appended to the {@code User-Agent} of every request so eparagony.pl can
 * tell which client library is calling.
 */
public final class SdkVersion {

    private static final String NAME = "eparagony-java-sdk";
    private static final String UNKNOWN_VERSION = "dev";

    private SdkVersion() {
    }

    /** The token appended to the caller's own User-Agent, e.g. {@code eparagony-java-sdk/0.1.0}. */
    public static String userAgentToken() {
        return NAME + "/" + version();
    }

    /**
     * The implementation version from the jar or module descriptor, or {@code "dev"} when running
     * from a build directory.
     *
     * <p>The module descriptor is consulted first: {@code Package.getImplementationVersion()} returns
     * {@code null} for a named module on the module path, so relying on it alone silently reports
     * {@code "dev"} to every modular consumer.
     */
    public static String version() {
        Optional<String> fromModule = Optional.ofNullable(SdkVersion.class.getModule())
                .map(Module::getDescriptor)
                .flatMap(java.lang.module.ModuleDescriptor::rawVersion);
        return fromModule
                .or(() -> Optional.ofNullable(SdkVersion.class.getPackage())
                        .map(Package::getImplementationVersion))
                .orElse(UNKNOWN_VERSION);
    }
}
