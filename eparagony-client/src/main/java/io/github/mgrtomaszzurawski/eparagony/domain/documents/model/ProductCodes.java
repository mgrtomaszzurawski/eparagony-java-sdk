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

import java.util.Optional;

/**
 * The product identifiers a line can carry beyond its printed name.
 *
 * <p>Grouped rather than spread across the line's own components: there are seven of them, they are
 * all optional, and they are all the same kind of thing. The API's integration guide asks explicitly
 * that these be supplied wherever the source system has them — they are what makes a receipt useful
 * for returns, warranty claims and analytics rather than merely fiscal.
 *
 * @param ean the EAN barcode
 * @param sku the seller's internal stock code
 * @param plu the register's price-lookup code
 * @param pkwiu the Polish statistical classification code
 * @param cn the Combined Nomenclature customs code
 * @param dataMatrix a DataMatrix code, where the goods carry one
 * @param externalId an identifier from the seller's own system
 */
public record ProductCodes(
        String ean,
        String sku,
        String plu,
        String pkwiu,
        String cn,
        String dataMatrix,
        String externalId) {

    private static final ProductCodes NONE = new ProductCodes(null, null, null, null, null, null, null);

    /** No codes at all. */
    public static ProductCodes none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> eanIfPresent() {
        return Optional.ofNullable(ean);
    }

    public Optional<String> skuIfPresent() {
        return Optional.ofNullable(sku);
    }

    /** {@code true} when no code at all was supplied. */
    public boolean isEmpty() {
        return ean == null && sku == null && plu == null && pkwiu == null
                && cn == null && dataMatrix == null && externalId == null;
    }

    /** Builder for {@link ProductCodes}. */
    public static final class Builder {

        private String ean;
        private String sku;
        private String plu;
        private String pkwiu;
        private String cn;
        private String dataMatrix;
        private String externalId;

        private Builder() {
        }

        public Builder ean(String value) {
            this.ean = value;
            return this;
        }

        public Builder sku(String value) {
            this.sku = value;
            return this;
        }

        public Builder plu(String value) {
            this.plu = value;
            return this;
        }

        public Builder pkwiu(String value) {
            this.pkwiu = value;
            return this;
        }

        public Builder cn(String value) {
            this.cn = value;
            return this;
        }

        public Builder dataMatrix(String value) {
            this.dataMatrix = value;
            return this;
        }

        public Builder externalId(String value) {
            this.externalId = value;
            return this;
        }

        public ProductCodes build() {
            return new ProductCodes(ean, sku, plu, pkwiu, cn, dataMatrix, externalId);
        }
    }
}
