package com.erp.sales.application;

import com.erp.ledger.api.SequenceAllocator;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.sales.domain.SalesOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Creates and confirms sales orders. An SO is a commitment and posts nothing. */
@Service
public class SalesOrderService {

    private static final String SEQUENCE_SCOPE = "SALES_ORDER";

    private final SalesOrderRepository salesOrderRepository;
    private final SequenceAllocator sequenceAllocator;
    private final MasterDataQuery masterDataQuery;

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                             SequenceAllocator sequenceAllocator,
                             MasterDataQuery masterDataQuery) {
        this.salesOrderRepository = salesOrderRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.masterDataQuery = masterDataQuery;
    }

    public record SoLineInput(Long itemId, BigDecimal qtyOrdered, BigDecimal unitPrice) {
    }

    @Transactional
    public SalesOrder createOrder(Long partnerId, List<SoLineInput> lines, LocalDate orderDate,
                                  String actor) {
        if (masterDataQuery.findPartner(partnerId).filter(p -> p.customer()).isEmpty()) {
            throw new SalesValidationException("partner " + partnerId + " is not a customer");
        }
        if (lines == null || lines.isEmpty()) {
            throw new SalesValidationException("a sales order needs at least one line");
        }
        String soNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        SalesOrder order = new SalesOrder(soNumber, partnerId, orderDate);
        for (SoLineInput line : lines) {
            if (masterDataQuery.findItem(line.itemId()).isEmpty()) {
                throw new SalesValidationException("unknown item " + line.itemId());
            }
            order.addLine(line.itemId(), line.qtyOrdered(), line.unitPrice());
        }
        return salesOrderRepository.saveAndFlush(order);
    }

    @Transactional
    public SalesOrder confirm(Long soId, String actor) {
        SalesOrder order = getOrder(soId);
        order.confirm();
        return salesOrderRepository.saveAndFlush(order);
    }

    @Transactional(readOnly = true)
    public SalesOrder getOrder(Long soId) {
        return salesOrderRepository.findById(soId)
                .orElseThrow(() -> new SalesOrderNotFoundException(soId));
    }

    @Transactional(readOnly = true)
    public List<SalesOrder> listOrders() {
        return salesOrderRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "id"));
    }
}
