package io.github.mgrtomaszzurawski.eparagony.core.error;

/**
 * The SDK was assembled wrongly — a missing credential, a blank {@code posId}, a malformed base URL.
 * Raised eagerly at construction time, never mid-call, so a misconfiguration fails on the first line
 * of application startup rather than on the first document.
 *
 * <p>Remediation: fix the configuration and restart. Retrying cannot help.
 */
public final class EparagonyConfigurationException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public EparagonyConfigurationException(String message) {
        super(message);
    }
}
