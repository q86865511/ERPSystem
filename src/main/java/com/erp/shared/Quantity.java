package com.erp.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Quantity value object: exact decimal arithmetic at scale 6 with HALF_UP rounding.
 *
 * <p>The counterpart to {@link Money} for the quantity/cost dimension. Stock quantities and unit costs
 * use scale 6 (finer than money's scale 4) so moving-average cost stays precise; a value (quantity ×
 * unit cost) is money and rounds back to scale 4. Never use {@code float}/{@code double}.
 */
public final class Quantity implements Comparable<Quantity> {

    public static final int SCALE = 6;

    private final BigDecimal amount;

    private Quantity(BigDecimal amount) {
        this.amount = Objects.requireNonNull(amount, "amount").setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Quantity of(BigDecimal amount) {
        return new Quantity(amount);
    }

    public static Quantity of(String amount) {
        return new Quantity(new BigDecimal(amount));
    }

    public static Quantity zero() {
        return new Quantity(BigDecimal.ZERO);
    }

    public Quantity add(Quantity other) {
        Objects.requireNonNull(other, "other");
        return new Quantity(amount.add(other.amount));
    }

    public Quantity subtract(Quantity other) {
        Objects.requireNonNull(other, "other");
        return new Quantity(amount.subtract(other.amount));
    }

    /** Multiplies this quantity by a unit cost, yielding a money amount rounded to {@link Money#SCALE}. */
    public BigDecimal multiplyCost(BigDecimal unitCost) {
        Objects.requireNonNull(unitCost, "unitCost");
        return amount.multiply(unitCost).setScale(Money.SCALE, RoundingMode.HALF_UP);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Quantity other) {
        Objects.requireNonNull(other, "other");
        return amount.compareTo(other.amount) > 0;
    }

    public BigDecimal amount() {
        return amount;
    }

    @Override
    public int compareTo(Quantity o) {
        return amount.compareTo(o.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Quantity quantity)) {
            return false;
        }
        return amount.compareTo(quantity.amount) == 0;
    }

    @Override
    public int hashCode() {
        return amount.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
