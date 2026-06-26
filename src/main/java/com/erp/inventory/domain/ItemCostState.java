package com.erp.inventory.domain;

import com.erp.shared.Money;
import com.erp.shared.Quantity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static lombok.AccessLevel.PROTECTED;

/**
 * Moving weighted-average cost cache for one stocked item. The authoritative subledger is
 * {@code stock_ledger_entry}; this row is a per-item summary updated under a pessimistic lock so
 * concurrent movements serialize on it. Invariant: {@code totalValue == SUM(value_delta)} of the
 * item's STOCK-location legs, and {@code avgUnitCost} is derived from it.
 *
 * <p>Quantities/costs are scale 6; value is money (scale 4) so it reconciles exactly to the GL.
 */
@Entity
@Table(name = "item_cost_state")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ItemCostState {

    /** The masterdata item id — this is a natural primary key, not a generated one. */
    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "on_hand_qty", nullable = false, precision = 19, scale = 6)
    private BigDecimal onHandQty;

    @Column(name = "avg_unit_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal avgUnitCost;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Version
    private long version;

    public ItemCostState(Long itemId) {
        this.itemId = itemId;
        this.onHandQty = BigDecimal.ZERO.setScale(Quantity.SCALE);
        this.avgUnitCost = BigDecimal.ZERO.setScale(Quantity.SCALE);
        this.totalValue = BigDecimal.ZERO.setScale(Money.SCALE);
    }

    /**
     * Applies an incoming quantity at a known unit cost (a receipt or a stock gain) and returns the
     * value added — the value_delta of the STOCK leg. The average is re-derived from the new total.
     */
    public BigDecimal applyReceipt(Quantity qtyIn, BigDecimal unitCostIn) {
        if (!qtyIn.isPositive()) {
            throw new IllegalArgumentException("receipt quantity must be positive");
        }
        BigDecimal addedValue = qtyIn.multiplyCost(unitCostIn);
        this.onHandQty = onHandQty.add(qtyIn.amount());
        this.totalValue = totalValue.add(addedValue);
        recomputeAverage();
        return addedValue;
    }

    /**
     * Applies an outgoing quantity at the current average (an issue or a stock loss) and returns the
     * value removed (a positive magnitude). The average is unchanged unless the item drains to zero, in
     * which case the exact remaining value is removed so the cache lands precisely on zero.
     */
    public BigDecimal applyIssue(Quantity qtyOut) {
        if (!qtyOut.isPositive()) {
            throw new IllegalArgumentException("issue quantity must be positive");
        }
        Quantity onHand = Quantity.of(onHandQty);
        if (qtyOut.isGreaterThan(onHand)) {
            throw new IllegalStateException("cannot issue " + qtyOut + " of item " + itemId
                    + " with only " + onHandQty + " on hand");
        }
        boolean fullDrain = qtyOut.compareTo(onHand) == 0;
        BigDecimal valueOut = fullDrain ? totalValue : qtyOut.multiplyCost(avgUnitCost);
        this.onHandQty = onHandQty.subtract(qtyOut.amount());
        this.totalValue = totalValue.subtract(valueOut);
        if (onHandQty.signum() == 0) {
            this.avgUnitCost = BigDecimal.ZERO.setScale(Quantity.SCALE);
        }
        return valueOut;
    }

    /** True if issuing {@code qtyOut} would drive on-hand negative (the MVP blocks this). */
    public boolean wouldGoNegative(Quantity qtyOut) {
        return qtyOut.isGreaterThan(Quantity.of(onHandQty));
    }

    /**
     * Adjusts total value (and the derived average) without changing on-hand quantity — a purchase
     * price-variance revaluation. The value delta is money (scale 4) and may be negative.
     */
    public void applyRevaluation(BigDecimal valueDelta) {
        BigDecimal newValue = totalValue.add(valueDelta);
        if (newValue.signum() < 0) {
            throw new IllegalStateException("revaluation drives item " + itemId + " value negative");
        }
        this.totalValue = newValue;
        recomputeAverage();
    }

    private void recomputeAverage() {
        this.avgUnitCost = onHandQty.signum() == 0
                ? BigDecimal.ZERO.setScale(Quantity.SCALE)
                : totalValue.divide(onHandQty, Quantity.SCALE, RoundingMode.HALF_UP);
    }
}
