package com.erp.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

/**
 * A customer-return line: a returned quantity with the net, VAT and COGS amounts it reversed, linked to
 * the stock-return movement and journal entry it posted. All money fields are scale 4.
 */
@Entity
@Table(name = "customer_return_line")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class CustomerReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_return_id", nullable = false)
    private CustomerReturn customerReturn;

    @Column(name = "so_line_id", nullable = false)
    private Long soLineId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal qty;

    @Column(name = "line_net", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineNet;

    @Column(name = "line_vat", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineVat;

    @Column(name = "line_cogs", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineCogs;

    @Column(name = "movement_group_id")
    private UUID movementGroupId;

    @Column(name = "stock_journal_entry_id")
    private Long stockJournalEntryId;

    CustomerReturnLine(CustomerReturn customerReturn, Long soLineId, Long itemId, BigDecimal qty,
                       BigDecimal lineNet, BigDecimal lineVat, BigDecimal lineCogs, UUID movementGroupId,
                       Long stockJournalEntryId) {
        this.customerReturn = customerReturn;
        this.soLineId = soLineId;
        this.itemId = itemId;
        this.qty = qty;
        this.lineNet = lineNet;
        this.lineVat = lineVat;
        this.lineCogs = lineCogs;
        this.movementGroupId = movementGroupId;
        this.stockJournalEntryId = stockJournalEntryId;
    }
}
