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
 * lets the buyer see what they saved.
 *
 * <p><strong>The sign is the specification's, and it is the opposite of the intuitive reading:</strong>
 * a <em>negative</em> value is a rebate, a positive value is a markup. Use {@link #rebate} and
 * {@link #markup} rather than the constructor; they take the magnitude and apply the sign, so a
 * caller never has to remember which way round it goes.
 *
 * @param name what to print beside it, e.g. {@code "Rabat lojalnościowy"}
 * @param value signed, per the specification: negative reduces the line, positive increases it
 */
public record RebateOrMarkup(String name, Amount value) {

    public RebateOrMarkup {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a rebate must be named; it is printed on the receipt");
        }
    }

    /**
     * A discount of the given magnitude. Pass a positive amount — the negative sign the specification
     * requires is applied here.
     */
    public static RebateOrMarkup rebate(String name, Amount magnitude) {
        return new RebateOrMarkup(name, Amount.ofGrosze(-Math.absExact(magnitude.grosze())));
    }

    /**
     * A surcharge of the given magnitude. Pass a positive amount; it reaches the wire positive, which
     * is what the specification reads as a markup.
     */
    public static RebateOrMarkup markup(String name, Amount magnitude) {
        // absExact, not abs: Math.abs(Integer.MIN_VALUE) is itself negative, which would turn a
        // surcharge into a discount rather than failing.
        return new RebateOrMarkup(name, Amount.ofGrosze(Math.absExact(magnitude.grosze())));
    }

    /** {@code true} when this reduces the line — that is, when the signed value is negative. */
    public boolean isRebate() {
        return value.grosze() < 0;
    }
}
