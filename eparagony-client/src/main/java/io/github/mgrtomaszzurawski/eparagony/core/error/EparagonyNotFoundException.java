package io.github.mgrtomaszzurawski.eparagony.core.error;

/**
 * The addressed resource does not exist ({@code HTTP 404}) — a business outcome, not a system fault,
 * so it is worth catching on its own to distinguish "no such document" from "the call failed".
 *
 * <p>Remediation: check the identifier. Note that the API does <em>not</em> use 404 uniformly: a
 * document that exists but belongs to another client answers {@code 403}
 * ({@link EparagonyAccessDeniedException}), and a document issued under an older {@code X-Api-Version}
 * answers {@code 400} ({@link EparagonyValidationException}). Absence of this exception is therefore
 * not proof that a document exists.
 */
public final class EparagonyNotFoundException extends EparagonyException {

    private static final long serialVersionUID = 1L;

    public EparagonyNotFoundException(String message) {
        super(message);
    }
}
