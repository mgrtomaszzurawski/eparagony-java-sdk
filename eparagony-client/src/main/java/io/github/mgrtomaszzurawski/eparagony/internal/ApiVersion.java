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
