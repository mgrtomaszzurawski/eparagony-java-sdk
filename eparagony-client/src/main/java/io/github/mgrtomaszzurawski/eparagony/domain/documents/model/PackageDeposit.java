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

import io.github.mgrtomaszzurawski.eparagony.core.model.Amount;

import java.util.Objects;
import java.util.Optional;

/**
 * A returnable-packaging deposit line — the Polish <em>kaucja</em> on bottles and crates.
 *
 * <p>Deliberately outside the fiscal lines: a deposit is not a sale, it does not carry VAT, and the
 * register accounts for it separately. The same shape covers both directions, which is why the
 * receipt has two lists: {@code packageReturns} for what the customer brought back and
 * {@code returnPackagesIssued} for what they took away.
 *
 * @param name what to print, e.g. {@code "Butelka zwrotna 0.5l"}
 * @param packageNumber the register's packaging type number
 * @param quantity how many
 * @param unitPrice the deposit per unit
 * @param totalLineValue the deposit total for this line
 * @param codes optional product identifiers
 */
public record PackageDeposit(
        String name,
        Integer packageNumber,
        int quantity,
        Amount unitPrice,
        Amount totalLineValue,
        ProductCodes codes) {

    public PackageDeposit {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(totalLineValue, "totalLineValue");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive but was " + quantity);
        }
        codes = codes == null ? ProductCodes.none() : codes;
    }

    /** A deposit line whose total is {@code unitPrice × quantity}. */
    public static PackageDeposit of(String name, int packageNumber, int quantity, Amount unitPrice) {
        return new PackageDeposit(name, packageNumber, quantity, unitPrice,
                Amount.ofGrosze(Math.multiplyExact(unitPrice.grosze(), quantity)), ProductCodes.none());
    }

    /** The same line with product codes attached. */
    public PackageDeposit identifiedBy(ProductCodes productCodes) {
        return new PackageDeposit(name, packageNumber, quantity, unitPrice, totalLineValue,
                Objects.requireNonNull(productCodes, "codes"));
    }

    public Optional<Integer> packageNumberIfPresent() {
        return Optional.ofNullable(packageNumber);
    }
}
