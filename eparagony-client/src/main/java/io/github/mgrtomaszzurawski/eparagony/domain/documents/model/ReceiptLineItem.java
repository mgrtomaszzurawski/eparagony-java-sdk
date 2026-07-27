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
 * An entry in a receipt's line list.
 *
 * <p>Sealed because the specification models this position as a two-way choice and nothing else
 * belongs there: either a sold item ({@link ReceiptLine}) or a standalone discount applied to part or
 * all of the sale ({@link ReceiptRebateLine}).
 *
 * <p>A rebate <em>line</em> is not the same thing as a {@link RebateOrMarkup} attached to a product
 * line. The latter discounts one item; this one sits between items and reduces the receipt.
 */
public sealed interface ReceiptLineItem permits ReceiptLine, ReceiptRebateLine {

    /** The amount this entry contributes to the sale total, signed. */
    io.github.mgrtomaszzurawski.eparagony.core.model.Amount contributionToTotal();
}
