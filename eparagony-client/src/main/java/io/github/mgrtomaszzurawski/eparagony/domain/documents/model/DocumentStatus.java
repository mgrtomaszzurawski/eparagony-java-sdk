package io.github.mgrtomaszzurawski.eparagony.domain.documents.model;

import io.github.mgrtomaszzurawski.eparagony.core.model.DocumentToken;
import io.github.mgrtomaszzurawski.eparagony.core.model.FiscalDeviceUniqueNumber;
import io.github.mgrtomaszzurawski.eparagony.core.model.TransactionToken;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The current state of a document, as reported by the status endpoint or a webhook notification.
 *
 * <p>Nearly everything beyond {@link #state()} is optional, because which fields are populated
 * depends on the state: a {@code PENDING} document has no fiscal document number yet, and an
 * {@code ERROR} one never will. Rather than model five near-identical shapes, the fields are
 * {@link Optional} and the state says which to expect.
 *
 * @param state where the document has got to
 * @param documentToken the document's identifier
 * @param transactionToken the transaction it belongs to
 * @param documentType the kind of document, when reported
 * @param processingMode how it is being processed, when reported
 * @param fiscalDeviceUniqueNumber the register that issued it, once fiscalized
 * @param fiscalDocumentId the register-scoped identifier, e.g. {@code ZBN1901007833/570}
 * @param fiscalDocumentNumber the fiscal document number
 * @param receiptNumber the receipt number
 * @param printed whether paper was produced
 * @param endTime when the register completed the document
 * @param orderId the order identifier supplied at issuance
 * @param merchantDocumentId the seller's document number supplied at issuance
 * @param documentUrl the customer-facing visualization page
 * @param errorMessage the failure detail, when {@link #state()} is {@link DocumentState#ERROR}
 */
public record DocumentStatus(
        DocumentState state,
        DocumentToken documentToken,
        TransactionToken transactionToken,
        String documentType,
        String processingMode,
        FiscalDeviceUniqueNumber fiscalDeviceUniqueNumber,
        String fiscalDocumentId,
        Integer fiscalDocumentNumber,
        Integer receiptNumber,
        Boolean printed,
        Instant endTime,
        String orderId,
        String merchantDocumentId,
        String documentUrl,
        String errorMessage) {

    public DocumentStatus {
        Objects.requireNonNull(state, "state");
    }

    /** {@code true} when the document reached the repository and may be shown to the customer. */
    public boolean isConfirmed() {
        return state == DocumentState.CONFIRMED;
    }

    /** {@code true} when no further transition is expected. */
    public boolean isTerminal() {
        return state.isTerminal();
    }

    public Optional<DocumentToken> documentTokenIfPresent() {
        return Optional.ofNullable(documentToken);
    }

    public Optional<TransactionToken> transactionTokenIfPresent() {
        return Optional.ofNullable(transactionToken);
    }

    public Optional<String> documentTypeIfPresent() {
        return Optional.ofNullable(documentType);
    }

    public Optional<String> processingModeIfPresent() {
        return Optional.ofNullable(processingMode);
    }

    public Optional<FiscalDeviceUniqueNumber> fiscalDeviceUniqueNumberIfPresent() {
        return Optional.ofNullable(fiscalDeviceUniqueNumber);
    }

    public Optional<String> fiscalDocumentIdIfPresent() {
        return Optional.ofNullable(fiscalDocumentId);
    }

    public Optional<Integer> fiscalDocumentNumberIfPresent() {
        return Optional.ofNullable(fiscalDocumentNumber);
    }

    public Optional<Integer> receiptNumberIfPresent() {
        return Optional.ofNullable(receiptNumber);
    }

    public Optional<Boolean> printedIfPresent() {
        return Optional.ofNullable(printed);
    }

    public Optional<Instant> endTimeIfPresent() {
        return Optional.ofNullable(endTime);
    }

    public Optional<String> orderIdIfPresent() {
        return Optional.ofNullable(orderId);
    }

    public Optional<String> merchantDocumentIdIfPresent() {
        return Optional.ofNullable(merchantDocumentId);
    }

    public Optional<String> documentUrlIfPresent() {
        return Optional.ofNullable(documentUrl);
    }

    public Optional<String> errorMessageIfPresent() {
        return Optional.ofNullable(errorMessage);
    }
}
