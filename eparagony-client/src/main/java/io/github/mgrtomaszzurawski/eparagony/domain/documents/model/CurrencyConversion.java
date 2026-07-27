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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The receipt total restated in another currency, printed for the customer's benefit.
 *
 * <p>Informational only: the fiscal document is still denominated in the register's currency, and this
 * changes neither the total nor the tax. It is what lets a foreign customer see what they paid in
 * their own money.
 *
 * @param currency the target currency, ISO-4217
 * @param exchangeRate the rate applied
 * @param afterConversion the receipt total in that currency
 */
public record CurrencyConversion(String currency, BigDecimal exchangeRate, Amount afterConversion) {

    public CurrencyConversion {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(exchangeRate, "exchangeRate");
        Objects.requireNonNull(afterConversion, "afterConversion");
        if (exchangeRate.signum() <= 0) {
            throw new IllegalArgumentException("an exchange rate must be positive but was " + exchangeRate);
        }
    }
}
