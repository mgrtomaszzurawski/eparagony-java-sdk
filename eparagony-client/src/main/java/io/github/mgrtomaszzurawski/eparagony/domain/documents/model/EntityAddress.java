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
 * A postal address as an invoice states it, for the seller, the buyer or any additional party.
 *
 * <p>Structured rather than a single string, because the API is: an invoice is a legal document and
 * an address that cannot be parsed into its parts cannot be validated against a tax register.
 *
 * @param street the street name
 * @param number the building number
 * @param apartment the flat or unit, where there is one
 * @param postalCode the postal code
 * @param postCity the post-office town, where it differs from {@code city}
 * @param city the town
 * @param regionOrState the region, province or state, where the country has them
 * @param country the country, as the invoice states it
 */
public record EntityAddress(
        String street,
        String number,
        String apartment,
        String postalCode,
        String postCity,
        String city,
        String regionOrState,
        String country) {

    public EntityAddress {
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(postalCode, "postalCode");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(country, "country");
    }

    /** The five parts every invoice address must state. */
    public static EntityAddress of(String street, String number, String postalCode, String city,
            String country) {
        return new EntityAddress(street, number, null, postalCode, null, city, null, country);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> apartmentIfPresent() {
        return Optional.ofNullable(apartment);
    }

    public Optional<String> postCityIfPresent() {
        return Optional.ofNullable(postCity);
    }

    public Optional<String> regionOrStateIfPresent() {
        return Optional.ofNullable(regionOrState);
    }

    /** Builder for {@link EntityAddress}. */
    public static final class Builder {

        private String street;
        private String number;
        private String apartment;
        private String postalCode;
        private String postCity;
        private String city;
        private String regionOrState;
        private String country;

        private Builder() {
        }

        public Builder street(String value) {
            this.street = Objects.requireNonNull(value, "street");
            return this;
        }

        public Builder number(String value) {
            this.number = Objects.requireNonNull(value, "number");
            return this;
        }

        public Builder apartment(String value) {
            this.apartment = Objects.requireNonNull(value, "apartment");
            return this;
        }

        public Builder postalCode(String value) {
            this.postalCode = Objects.requireNonNull(value, "postalCode");
            return this;
        }

        /** The post-office town, when it differs from the town the address is in. */
        public Builder postCity(String value) {
            this.postCity = Objects.requireNonNull(value, "postCity");
            return this;
        }

        public Builder city(String value) {
            this.city = Objects.requireNonNull(value, "city");
            return this;
        }

        public Builder regionOrState(String value) {
            this.regionOrState = Objects.requireNonNull(value, "regionOrState");
            return this;
        }

        public Builder country(String value) {
            this.country = Objects.requireNonNull(value, "country");
            return this;
        }

        public EntityAddress build() {
            return new EntityAddress(street, number, apartment, postalCode, postCity, city,
                    regionOrState, country);
        }
    }
}
