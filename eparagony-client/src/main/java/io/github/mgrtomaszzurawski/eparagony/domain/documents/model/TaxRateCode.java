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
