package com.erp.purchasing.application;

import com.erp.ledger.api.SequenceAllocator;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.purchasing.domain.PurchaseOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Creates and confirms purchase orders. A PO is a commitment and posts nothing. */
@Service
public class PurchaseOrderService {

    private static final String SEQUENCE_SCOPE = "PURCHASE_ORDER";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SequenceAllocator sequenceAllocator;
    private final MasterDataQuery masterDataQuery;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SequenceAllocator sequenceAllocator,
                                MasterDataQuery masterDataQuery) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.masterDataQuery = masterDataQuery;
    }

    public record PoLineInput(Long itemId, BigDecimal qtyOrdered, BigDecimal unitPrice) {
    }

    @Transactional
    public PurchaseOrder createOrder(Long partnerId, List<PoLineInput> lines, LocalDate orderDate,
                                     String actor) {
        if (masterDataQuery.findPartner(partnerId).filter(p -> p.vendor()).isEmpty()) {
            throw new PurchasingValidationException("partner " + partnerId + " is not a vendor");
        }
        if (lines == null || lines.isEmpty()) {
            throw new PurchasingValidationException("a purchase order needs at least one line");
        }
        String poNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        PurchaseOrder order = new PurchaseOrder(poNumber, partnerId, orderDate);
        for (PoLineInput line : lines) {
            if (masterDataQuery.findItem(line.itemId()).isEmpty()) {
                throw new PurchasingValidationException("unknown item " + line.itemId());
            }
            order.addLine(line.itemId(), line.qtyOrdered(), line.unitPrice());
        }
        return purchaseOrderRepository.saveAndFlush(order);
    }

    @Transactional
    public PurchaseOrder confirm(Long poId, String actor) {
        PurchaseOrder order = getOrder(poId);
        order.confirm();
        return purchaseOrderRepository.saveAndFlush(order);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getOrder(Long poId) {
        return purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(poId));
    }
}
