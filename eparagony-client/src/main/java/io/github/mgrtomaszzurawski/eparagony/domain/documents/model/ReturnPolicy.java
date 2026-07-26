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

import java.util.Objects;
import java.util.Optional;

/**
 * The return terms printed for a product, or for the whole receipt.
 *
 * @param productReturnDays how many days the buyer has to return it
 * @param inStoreReturnsOffered whether returns are accepted in a physical shop
 * @param additionalDescription free text printed alongside, e.g. a link to the full terms
 */
public record ReturnPolicy(Integer productReturnDays, Boolean inStoreReturnsOffered,
        String additionalDescription) {

    /** A policy stating only the return window. */
    public static ReturnPolicy ofDays(int productReturnDays) {
        if (productReturnDays < 0) {
            throw new IllegalArgumentException(
                    "productReturnDays must not be negative but was " + productReturnDays);
        }
        return new ReturnPolicy(productReturnDays, null, null);
    }

    /** A policy stating the window, whether in-store returns are offered, and the printed terms. */
    public static ReturnPolicy of(int productReturnDays, boolean inStoreReturnsOffered,
            String additionalDescription) {
        return new ReturnPolicy(productReturnDays, inStoreReturnsOffered,
                Objects.requireNonNull(additionalDescription, "additionalDescription"));
    }

    public Optional<Integer> productReturnDaysIfStated() {
        return Optional.ofNullable(productReturnDays);
    }

    public Optional<Boolean> inStoreReturnsOfferedIfStated() {
        return Optional.ofNullable(inStoreReturnsOffered);
    }

    public Optional<String> additionalDescriptionIfStated() {
        return Optional.ofNullable(additionalDescription);
    }
}
