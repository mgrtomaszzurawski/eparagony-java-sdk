package io.github.mgrtomaszzurawski.eparagony.domain.documents.model;

/**
 * A cash register's VAT rate slot, {@code A} through {@code G}.
 *
 * <p>The API deals in letters, never percentages, and this is deliberate on their part: the letters
 * are what the physical cash register is programmed with. If the statutory rates change, the register
 * is reprogrammed and every integration keeps working — whereas an integration sending {@code 23}
 * would have to be redeployed. A line item names a slot; {@link TaxRateTable} says what each slot
 * currently means.
 *
 * <p>The near-universal Polish configuration is {@code A}=23%, {@code B}=8%, {@code C}=5%,
 * {@code D}=0%, {@code E}=exempt, with {@code F} and {@code G} unused.
 */
public enum TaxRateCode {
    A, B, C, D, E, F, G
}
