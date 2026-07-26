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
 * A party named on an invoice besides the seller and the buyer — a factor, an agent, a local-authority
 * unit, a payer who is not the recipient.
 *
 * <p>These exist because Polish invoicing frequently involves someone who is neither of the two
 * obvious parties, and KSeF wants them named rather than implied.
 *
 * @param role what this party is to the transaction
 * @param otherRoleDescription what the role is, when it is {@link Role#OTHER}
 * @param name the party's name
 * @param address the party's address
 * @param tin the party's tax identification number
 * @param countryCode the country its identifiers belong to
 * @param internalId the seller's own identifier for it
 * @param otherId any further identifier the invoice must carry
 */
public record InvoiceParty(
        Role role,
        String otherRoleDescription,
        String name,
        EntityAddress address,
        String tin,
        String countryCode,
        String internalId,
        String otherId) {

    /** What an additional party is to the transaction. */
    public enum Role {
        FACTOR, RECIPIENT, PAYER, LOCAL_AUTHORITY_UNIT, MEMBER, OTHER;

        /** The literal the API expects. */
        public String wireValue() {
            return name();
        }
    }

    public InvoiceParty {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("an invoice party must be named");
        }
        if (role == Role.OTHER && (otherRoleDescription == null || otherRoleDescription.isBlank())) {
            // OTHER without a description names nothing; the invoice would state a party whose
            // relationship to the transaction is unrecorded.
            throw new IllegalArgumentException(
                    "role OTHER requires otherRoleDescription saying what the role actually is");
        }
    }

    public static Builder builder(Role role, String name) {
        return new Builder(role, name);
    }

    public Optional<String> otherRoleDescriptionIfPresent() {
        return Optional.ofNullable(otherRoleDescription);
    }

    public Optional<EntityAddress> addressIfPresent() {
        return Optional.ofNullable(address);
    }

    public Optional<String> tinIfPresent() {
        return Optional.ofNullable(tin);
    }

    public Optional<String> countryCodeIfPresent() {
        return Optional.ofNullable(countryCode);
    }

    public Optional<String> internalIdIfPresent() {
        return Optional.ofNullable(internalId);
    }

    public Optional<String> otherIdIfPresent() {
        return Optional.ofNullable(otherId);
    }

    /** Builder for {@link InvoiceParty}. */
    public static final class Builder {

        private final Role role;
        private final String name;
        private String otherRoleDescription;
        private EntityAddress address;
        private String tin;
        private String countryCode;
        private String internalId;
        private String otherId;

        private Builder(Role role, String name) {
            this.role = Objects.requireNonNull(role, "role");
            this.name = Objects.requireNonNull(name, "name");
        }

        /** Says what the role is. Required when the role is {@link Role#OTHER}. */
        public Builder otherRoleDescription(String value) {
            this.otherRoleDescription = Objects.requireNonNull(value, "otherRoleDescription");
            return this;
        }

        public Builder address(EntityAddress value) {
            this.address = Objects.requireNonNull(value, "address");
            return this;
        }

        public Builder tin(String value) {
            this.tin = Objects.requireNonNull(value, "tin");
            return this;
        }

        public Builder countryCode(String value) {
            this.countryCode = Objects.requireNonNull(value, "countryCode");
            return this;
        }

        public Builder internalId(String value) {
            this.internalId = Objects.requireNonNull(value, "internalId");
            return this;
        }

        public Builder otherId(String value) {
            this.otherId = Objects.requireNonNull(value, "otherId");
            return this;
        }

        public InvoiceParty build() {
            return new InvoiceParty(role, otherRoleDescription, name, address, tin, countryCode,
                    internalId, otherId);
        }
    }
}
