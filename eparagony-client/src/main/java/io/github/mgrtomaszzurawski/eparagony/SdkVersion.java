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
                .flatMap(descriptor -> descriptor.rawVersion());
        return fromModule
                .or(() -> Optional.ofNullable(SdkVersion.class.getPackage())
                        .map(Package::getImplementationVersion))
                .orElse(UNKNOWN_VERSION);
    }
}
