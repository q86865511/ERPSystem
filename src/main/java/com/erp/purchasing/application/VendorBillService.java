package com.erp.purchasing.application;

import com.erp.inventory.api.StockPosting;
import com.erp.ledger.api.JournalEntryRequest;
import com.erp.ledger.api.JournalEntryRequest.Line;
import com.erp.ledger.api.LedgerPosting;
import com.erp.ledger.api.PostingResult;
import com.erp.ledger.api.SequenceAllocator;
import com.erp.masterdata.api.ItemView;
import com.erp.masterdata.api.MasterDataQuery;
import com.erp.purchasing.api.PayableBillView;
import com.erp.purchasing.api.PayableDocuments;
import com.erp.purchasing.domain.BillMatchStatus;
import com.erp.purchasing.domain.GrnLine;
import com.erp.purchasing.domain.PoLine;
import com.erp.purchasing.domain.PurchaseOrder;
import com.erp.purchasing.domain.VendorBill;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Posts vendor bills. A bill matches its lines (FIFO) to received-but-unbilled receipts, clears the
 * GR-IR credit at receipt cost, records Input VAT and the AP credit (both tagged with the vendor), and
 * routes any price variance to the item's inventory account — revaluing inventory through the stock
 * port <em>before</em> posting the journal entry so the cost-state lock precedes the journal sequence.
 * Everything commits in one transaction. Also serves the {@link PayableDocuments} port for payments.
 */
@Service
public class VendorBillService implements PayableDocuments {

    private static final String SEQUENCE_SCOPE = "VENDOR_BILL";
    private static final String SOURCE_DOC_TYPE = "VENDOR_BILL";
    private static final String GR_IR_ACCOUNT = "2150";
    private static final String AP_ACCOUNT = "2100";
    private static final String INPUT_VAT_ACCOUNT = "1450";
    private static final int MONEY_SCALE = 4;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GrnLineRepository grnLineRepository;
    private final VendorBillRepository vendorBillRepository;
    private final SequenceAllocator sequenceAllocator;
    private final LedgerPosting ledgerPosting;
    private final StockPosting stockPosting;
    private final MasterDataQuery masterDataQuery;

    public VendorBillService(PurchaseOrderRepository purchaseOrderRepository,
                             GrnLineRepository grnLineRepository,
                             VendorBillRepository vendorBillRepository,
                             SequenceAllocator sequenceAllocator,
                             LedgerPosting ledgerPosting,
                             StockPosting stockPosting,
                             MasterDataQuery masterDataQuery) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.grnLineRepository = grnLineRepository;
        this.vendorBillRepository = vendorBillRepository;
        this.sequenceAllocator = sequenceAllocator;
        this.ledgerPosting = ledgerPosting;
        this.stockPosting = stockPosting;
        this.masterDataQuery = masterDataQuery;
    }

    public record BillLineInput(Long poLineId, BigDecimal qty, BigDecimal unitPrice) {
    }

    @Transactional
    public VendorBill postBill(Long poId, List<BillLineInput> billLines, String taxRateCode,
                               LocalDate postingDate, String actor) {
        if (billLines == null || billLines.isEmpty()) {
            throw new PurchasingValidationException("a vendor bill needs at least one line");
        }
        PurchaseOrder order = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(poId));
        BigDecimal rate = masterDataQuery.findTaxRate(taxRateCode)
                .orElseThrow(() -> new PurchasingValidationException("unknown tax rate " + taxRateCode));

        String billNumber = sequenceAllocator.next(SEQUENCE_SCOPE);
        VendorBill bill = new VendorBill(billNumber, poId, order.getPartnerId(), postingDate);

        BigDecimal totalGrIr = zero();
        BigDecimal totalVat = zero();
        BigDecimal totalGoods = zero();
        Map<String, BigDecimal> variancePerAccount = new LinkedHashMap<>();
        Map<Long, BigDecimal> variancePerItem = new LinkedHashMap<>();

        for (BillLineInput input : billLines) {
            PoLine poLine = order.lineById(input.poLineId());
            BigDecimal qty = input.qty();
            if (qty == null || qty.signum() <= 0) {
                throw new PurchasingValidationException("bill quantity must be positive");
            }
            BigDecimal grIrCleared = allocateAgainstReceipts(input.poLineId(), qty);
            order.applyBilling(input.poLineId(), qty);

            BigDecimal lineNet = qty.multiply(input.unitPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineNet.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineVariance = lineNet.subtract(grIrCleared);

            ItemView item = masterDataQuery.findItem(poLine.getItemId())
                    .orElseThrow(() -> new PurchasingValidationException(
                            "unknown item " + poLine.getItemId()));
            String inventoryAccount = masterDataQuery.resolveInventoryAccountCode(item.itemType());

            totalGrIr = totalGrIr.add(grIrCleared);
            totalVat = totalVat.add(lineVat);
            totalGoods = totalGoods.add(lineNet);
            BillMatchStatus matchStatus = BillMatchStatus.MATCHED;
            if (lineVariance.signum() != 0) {
                variancePerAccount.merge(inventoryAccount, lineVariance, BigDecimal::add);
                variancePerItem.merge(poLine.getItemId(), lineVariance, BigDecimal::add);
                matchStatus = BillMatchStatus.PRICE_VARIANCE;
            }
            bill.addLine(input.poLineId(), poLine.getItemId(), qty, input.unitPrice(), lineNet, lineVat,
                    matchStatus);
        }

        BigDecimal gross = totalGoods.add(totalVat);

        // Revalue varianced items BEFORE posting the entry (lock order: cost-state then journal seq).
        variancePerItem.forEach((itemId, valueDelta) ->
                stockPosting.revalue(itemId, valueDelta, SOURCE_DOC_TYPE, billNumber));

        List<Line> entryLines = new ArrayList<>();
        entryLines.add(new Line(GR_IR_ACCOUNT, totalGrIr, null, "GR-IR clearing", order.getPartnerId()));
        variancePerAccount.forEach((account, variance) -> {
            if (variance.signum() > 0) {
                entryLines.add(new Line(account, variance, null, "purchase price variance", null));
            } else {
                entryLines.add(new Line(account, null, variance.negate(), "purchase price variance", null));
            }
        });
        if (totalVat.signum() > 0) {
            entryLines.add(new Line(INPUT_VAT_ACCOUNT, totalVat, null, "input VAT", null));
        }
        entryLines.add(new Line(AP_ACCOUNT, null, gross, "accounts payable", order.getPartnerId()));

        JournalEntryRequest request = new JournalEntryRequest(null, postingDate,
                "vendor bill " + billNumber, null, SOURCE_DOC_TYPE, billNumber, "BILL", entryLines);
        PostingResult posting = ledgerPosting.post(request, actor);

        bill.markPosted(totalGoods, totalVat, gross, posting.entryId());
        purchaseOrderRepository.save(order);
        return vendorBillRepository.saveAndFlush(bill);
    }

    /** FIFO-allocates a billed quantity to a PO line's open receipts, returning the GR-IR amount cleared. */
    private BigDecimal allocateAgainstReceipts(Long poLineId, BigDecimal qty) {
        BigDecimal remaining = qty;
        BigDecimal grIrCleared = zero();
        for (GrnLine grnLine : grnLineRepository.findByPoLineIdOrderByIdAsc(poLineId)) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal available = grnLine.outstandingToBill();
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal allocated = available.min(remaining);
            grIrCleared = grIrCleared.add(
                    allocated.multiply(grnLine.getUnitCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            grnLine.bill(allocated);
            remaining = remaining.subtract(allocated);
        }
        if (remaining.signum() > 0) {
            throw new PurchasingValidationException(
                    "not enough received-but-unbilled quantity to bill " + qty + " on po line " + poLineId);
        }
        return grIrCleared;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PayableBillView> findOpenBill(Long billId) {
        return vendorBillRepository.findById(billId).map(VendorBillService::toPayableView);
    }

    @Override
    @Transactional
    public void applyPayment(Long billId, BigDecimal amount) {
        VendorBill bill = vendorBillRepository.findById(billId)
                .orElseThrow(() -> new VendorBillNotFoundException(billId));
        bill.applyPayment(amount);
        vendorBillRepository.saveAndFlush(bill);
    }

    @Transactional(readOnly = true)
    public VendorBill getBill(Long id) {
        return vendorBillRepository.findById(id)
                .orElseThrow(() -> new VendorBillNotFoundException(id));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    private static PayableBillView toPayableView(VendorBill bill) {
        return new PayableBillView(bill.getId(), bill.getBillNumber(), bill.getPartnerId(),
                bill.getGrossAmount(), bill.getAmountPaid(), bill.openBalance(), bill.getStatus().name());
    }
}
