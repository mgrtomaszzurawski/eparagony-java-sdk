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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The non-fiscal extras a receipt can carry: loyalty movements, gift cards used, and terms that apply
 * to the whole document rather than one line.
 *
 * <p>None of it affects the fiscal total. All of it affects whether the receipt is useful to the
 * customer afterwards — which is the point of an e-receipt over a paper one.
 *
 * @param consumerLoyalty loyalty programme movements recorded against this sale
 * @param giftCards gift cards presented
 * @param recyclingDb the recycling database identifier, where the seller is registered in one
 * @param globalReturnPolicy return terms for the whole receipt
 * @param warranty warranty terms for the whole receipt
 */
public record ReceiptExtensions(
        List<LoyaltyMovement> consumerLoyalty,
        List<GiftCardUse> giftCards,
        String recyclingDb,
        ReturnPolicy globalReturnPolicy,
        Warranty warranty) {

    private static final ReceiptExtensions EMPTY =
            new ReceiptExtensions(List.of(), List.of(), null, null, null);

    /**
     * A loyalty programme movement recorded on the receipt.
     *
     * @param additionalContent a line printed beside the movement. Deliberately an
     *     {@link AdditionalDescription} and not a {@link ContentLine}: this slot accepts only the
     *     text-or-graphic pair, so a five-way content line would have to be flattened, and a
     *     separator or key-value line would silently vanish.
     */
    public record LoyaltyMovement(String id, String name, BigDecimal pointsAdded,
            BigDecimal newBalance, AdditionalDescription additionalContent) {

        public LoyaltyMovement {
            Objects.requireNonNull(id, "id");
        }

        /**
         * Points moved by this sale, and the balance that leaves.
         *
         * <p>Decimal, not integer: the specification types both as strings and its own examples are
         * fractional ({@code -20.98}, {@code 1234.56}). Programmes that award part-points exist, and
         * rounding them here would print a number that disagrees with the programme. A negative
         * {@code pointsAdded} is a redemption.
         */
        public static LoyaltyMovement of(String id, String name, BigDecimal pointsAdded,
                BigDecimal newBalance) {
            return new LoyaltyMovement(id, name, pointsAdded, newBalance, null);
        }

        /** The same movement with a line printed beside it — text, or a graphic such as a QR code. */
        public LoyaltyMovement printing(AdditionalDescription content) {
            return new LoyaltyMovement(id, name, pointsAdded, newBalance,
                    Objects.requireNonNull(content, "additionalContent"));
        }
    }

    /** A gift card presented against the sale. */
    public record GiftCardUse(String giftCardNo, Amount giftCardValue) {

        public GiftCardUse {
            Objects.requireNonNull(giftCardNo, "giftCardNo");
            Objects.requireNonNull(giftCardValue, "giftCardValue");
        }
    }

    public ReceiptExtensions {
        consumerLoyalty = List.copyOf(Objects.requireNonNullElse(consumerLoyalty, List.of()));
        giftCards = List.copyOf(Objects.requireNonNullElse(giftCards, List.of()));
    }

    /** No extensions; the receipt carries only its fiscal content. */
    public static ReceiptExtensions none() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** {@code true} when nothing was supplied, so the object can be left off the wire entirely. */
    public boolean isEmpty() {
        return consumerLoyalty.isEmpty() && giftCards.isEmpty() && recyclingDb == null
                && globalReturnPolicy == null && warranty == null;
    }

    public Optional<String> recyclingDbIfPresent() {
        return Optional.ofNullable(recyclingDb);
    }

    public Optional<ReturnPolicy> globalReturnPolicyIfPresent() {
        return Optional.ofNullable(globalReturnPolicy);
    }

    public Optional<Warranty> warrantyIfPresent() {
        return Optional.ofNullable(warranty);
    }

    /** Builder for {@link ReceiptExtensions}. */
    public static final class Builder {

        private final List<LoyaltyMovement> consumerLoyalty = new ArrayList<>();
        private final List<GiftCardUse> giftCards = new ArrayList<>();
        private String recyclingDb;
        private ReturnPolicy globalReturnPolicy;
        private Warranty warranty;

        private Builder() {
        }

        public Builder addLoyaltyMovement(LoyaltyMovement movement) {
            consumerLoyalty.add(Objects.requireNonNull(movement, "movement"));
            return this;
        }

        public Builder addGiftCard(GiftCardUse giftCard) {
            giftCards.add(Objects.requireNonNull(giftCard, "giftCard"));
            return this;
        }

        /** The seller's recycling database identifier. */
        public Builder recyclingDb(String value) {
            this.recyclingDb = Objects.requireNonNull(value, "recyclingDb");
            return this;
        }

        /** Return terms covering the whole receipt, rather than one product. */
        public Builder globalReturnPolicy(ReturnPolicy value) {
            this.globalReturnPolicy = Objects.requireNonNull(value, "globalReturnPolicy");
            return this;
        }

        /** Warranty terms covering the whole receipt. */
        public Builder warranty(Warranty value) {
            this.warranty = Objects.requireNonNull(value, "warranty");
            return this;
        }

        public ReceiptExtensions build() {
            return new ReceiptExtensions(consumerLoyalty, giftCards, recyclingDb, globalReturnPolicy,
                    warranty);
        }
    }
}
