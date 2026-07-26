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

/**
 * A discount or surcharge applied to one line.
 *
 * <p>Itemized rather than folded into the price, which is what the tax authority expects and what
 * lets the buyer see what they saved. A negative {@code value} is a markup.
 *
 * @param name what to print beside it, e.g. {@code "Rabat lojalnościowy"}
 * @param value the amount taken off (or added, when negative)
 */
public record RebateOrMarkup(String name, Amount value) {

    public RebateOrMarkup {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a rebate must be named; it is printed on the receipt");
        }
    }

    /** A discount of the given amount. */
    public static RebateOrMarkup rebate(String name, Amount value) {
        return new RebateOrMarkup(name, value);
    }
}
