package com.erp.purchasing.application;

import com.erp.masterdata.api.MasterDataQuery;
import com.erp.masterdata.api.PartnerView;
import com.erp.purchasing.domain.GoodsReceipt;
import com.erp.purchasing.domain.GrnLine;
import com.erp.purchasing.domain.PoLine;
import com.erp.purchasing.domain.PurchaseOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side supplier on-time performance: for every received line that carried an expected delivery date,
 * whether the goods receipt posted on or before it — aggregated to an on-time percentage per vendor.
 */
@Service
@Transactional(readOnly = true)
public class SupplierPerformanceService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final MasterDataQuery masterDataQuery;

    public SupplierPerformanceService(GoodsReceiptRepository goodsReceiptRepository,
                                      PurchaseOrderRepository purchaseOrderRepository,
                                      MasterDataQuery masterDataQuery) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.masterDataQuery = masterDataQuery;
    }

    public List<SupplierPerformance> performance() {
        Map<Long, int[]> byPartner = new LinkedHashMap<>();  // partnerId -> [total, onTime]
        for (GoodsReceipt receipt : goodsReceiptRepository.findAll()) {
            PurchaseOrder order = purchaseOrderRepository.findById(receipt.getPurchaseOrderId())
                    .orElse(null);
            if (order == null) {
                continue;
            }
            for (GrnLine line : receipt.getLines()) {
                PoLine poLine = order.getLines().stream()
                        .filter(l -> l.getId().equals(line.getPoLineId())).findFirst().orElse(null);
                if (poLine == null || poLine.getExpectedDeliveryDate() == null) {
                    continue;
                }
                int[] counts = byPartner.computeIfAbsent(order.getPartnerId(), k -> new int[2]);
                counts[0]++;
                if (!receipt.getPostingDate().isAfter(poLine.getExpectedDeliveryDate())) {
                    counts[1]++;
                }
            }
        }

        List<SupplierPerformance> result = new ArrayList<>();
        byPartner.forEach((partnerId, counts) -> {
            String name = masterDataQuery.findPartner(partnerId).map(PartnerView::name)
                    .orElse("#" + partnerId);
            BigDecimal pct = counts[0] == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(counts[1]).multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(counts[0]), 1, RoundingMode.HALF_UP);
            result.add(new SupplierPerformance(partnerId, name, counts[0], counts[1], pct));
        });
        result.sort(Comparator.comparing(SupplierPerformance::onTimePct).reversed());
        return result;
    }
}
