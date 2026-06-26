# PROGRESS — 製造業 ERP(作品集專案)

## 目前狀態
**Phase 3(訂單到收款)完成** — 分 4 段 PR 交付(SO→Delivery、CustomerInvoice、Receipt+AR 帳齡、CustomerReturn)。採「延後 COGS(deferred COGS)」鏡像 GR-IR:出貨成本停在過渡科目 1340,開票才認 COGS,「出貨↔開票」對稱清零。**O2C 驗收達成(`ArReconciliationIT`)**:全鏈跑完庫存↓、COGS+收入+Output VAT 入帳、1340→0、AR→0、AR 子帳==1200、試算表平衡;客戶退貨(credit note)全鏈沖回零。`mvn verify` 全綠(Surefire 45、IT 43)。**Phase 5(報表與期間結)進行中** — **S1 完成**:新 read-side `reporting` 模組 + `ledger.api.GeneralLedgerQuery` 發布口;試算表(as-of 日期)、資產負債表(保留盈餘動態 = 收入−費用,無期末結轉)、損益表、總帳明細 drill-down;REST `/api/reporting`。`FinancialStatementsIT` 驗會計恆等式(TB 平、BS 平、淨利=收入−費用)+ 場景增量。`mvn verify` 全綠(Surefire 55、IT 50)。下一棒 S2 對帳健康檢查(hero)、S3 soft-close。

**Phase 4 製造完成** —— 分 3 段 PR(BOM+release+領料、完工成本滾算+再訂點+對帳、工單取消)。`manufacturing` 模組 BOM→WorkOrder(release 快照)→領料(`Dr 1320 / Cr 1310`)→完工(成本滾算 `Dr 1330 / Cr 1320`,餘差掃 5930)→可取消(反向領料 `Dr 1310 / Cr 1320`)。**製造驗收達成(`MfgReconciliationIT`)**:原料↓、成品依滾算成本↑、**WIP(1320)→0**、庫存子帳==GL、試算表平衡。再訂點報表上線(經新 `inventory.api.InventoryQuery`)。**🎉 路線圖「買→做→賣」最低可展示里程碑(Phase 4 結束)達成。** `mvn verify` 全綠(Surefire 54、IT 49)。下一棒(待指示):Phase 5 報表與期間結。
**Phase 2(採購到付款)完成** — 分 4 段 PR 交付(Partner/種子、purchasing PO→GR、VendorBill、payments)。**Phase 2 驗收達成(`ApReconciliationIT`)**:PO→GR→VendorBill→Payment 全鏈跑完後 **GR-IR→0、AP→0、AP 子帳==GL 2100、試算表平衡、庫存上升**;採購價差走庫存重估、GL 帶 partner 維度。
Phase 1(商品與庫存)已完成:`inventory` 移動加權平均、append-only 子帳、對帳達成「庫存帳值==GL」。Phase 2 計畫見 `~/.claude/plans/phase-1-iridescent-ember.md`,總路線圖見 `~/.claude/plans/pm-erp-enchanted-aurora.md`。

## 已完成
- [2026-06-27] 📊 P5-S1 財務報表(read-side reporting 模組)(Phase 5 Stage 1)
  - 新 `ledger.api.GeneralLedgerQuery`/`AccountBalance`/`LedgerLineView`(發布 as-of 餘額與明細,`AccountBalance.naturalBalance()` 依 normal balance 帶號)+ `GeneralLedgerQueryService`/`GeneralLedgerRepository`(native query,posting_date ≤ asOf)。
  - 新 `reporting` 模組(read-side leaf,只用他模組 `*.api`):`ReportingService` 產出試算表(as-of)、損益表(收入/費用/淨利)、資產負債表(資產=負債+權益,權益含「本期損益」= 收入−費用,保留盈餘動態、無期末結轉)、總帳 drill-down;REST `/api/reporting/{trial-balance,income-statement,balance-sheet,general-ledger/{code}}`。
  - ArchUnit:`reporting` 只依賴各模組 `*.api`;既有跨模組規則擴含 `..reporting..`(無人可依賴 reporting)。
  - `FinancialStatementsIT`(會計恆等式 + 場景增量,用非子帳科目避免共用容器汙染)。`verify` 全綠(Surefire 55、IT 50)。
- [2026-06-27] 🛑 P4-S7 工單取消 / 反向領料(製造沖銷)(Phase 4 Stage 3,Phase 4 收尾)
  - `WorkOrderService.cancel(woId, stockLocationId,…)`:對 IN_PROGRESS 工單,逐已領 component 經 `StockPosting` MANUFACTURING_RETURN WIP→STOCK(以原領料成本 `Dr 1310 / Cr 1320`)退料回庫,使 WIP 歸零;WO→CANCELLED。RELEASED(未領料)直接取消、不過帳。append-only 沖銷,不改既有腿。REST `/api/manufacturing/work-orders/{id}/cancel`。
  - `WorkOrderCancelIT`:領料後取消 → 退料 `Dr1310/Cr1320`、raw 回補 100、WIP 該工單淨額→0、WO CANCELLED、TB 平。`verify` 全綠(Surefire 54、IT 49)。
- [2026-06-27] 🏭 P4-S6 WO 完工(成本滾算)+ 餘差 5930 + 再訂點 + 製造對帳(Phase 4 Stage 2)
  - `WorkOrderService.complete(woId, qtyProduced, fgStockLocationId,…)`:`rolledCost = totalComponentCost / qtyProduced`(6dp),經 `StockPosting` MANUFACTURING_RECEIPT WIP→STOCK 過 `Dr 1330 / Cr 1320`(成品進庫更新移動平均);殘差 `consumed − received` 掃 5930(`Dr/Cr 5930 vs 1320`)使 WIP 歸零;WO→DONE。整數情境餘差為 0(6dp 成本 × 整數量於 money scale 還原)。
  - **再訂點報表**:新 `inventory.api.InventoryQuery`/`ItemOnHand`(讀在庫,不外露 cost-state 實體)+ `InventoryQueryService`;`manufacturing.ReorderReportService`(join 在庫 + masterdata reorder_point,列出 ≤ 再訂點者)。REST `/api/manufacturing/work-orders/{id}/complete`、`/api/manufacturing/reorder-report`。
  - `WorkOrderCompletionIT`(`Dr1330/Cr1320`、FG 依滾算成本、WIP 該工單淨額→0、無 5930 變異)、**`MfgReconciliationIT`**(make 全鏈:原料↓、成品↑、WIP→0、庫存子帳==GL、TB 平)、`ReorderReportIT`。`verify` 全綠(Surefire 54、IT 48)。
- [2026-06-27] 🏭 P4-S5 BOM + WorkOrder release + 領料(WIP issue)(Phase 4 Stage 1)
  - 新 `manufacturing` 模組:domain `BillOfMaterials`/`BomComponent`(單階,output_qty/qty_per/scrap_pct 保留)、`WorkOrder`/`WorkOrderComponent`(狀態機 DRAFT→RELEASED→IN_PROGRESS→DONE→CANCELLED);`BomService.createBom`(自動 version)、`WorkOrderService.create/release/issue`。`release` 展開 BOM × qtyToProduce/outputQty → 快照 planned_qty(凍結);`issue` 逐 component 經 `StockPosting` MANUFACTURING_ISSUE STOCK→WIP 過 `Dr 1320 / Cr 1310`(移動平均),累計 `consumed_value` 供完工滾算,WO→IN_PROGRESS。REST `/api/manufacturing`。
  - 基礎:`InventoryMovementType` 加 `MANUFACTURING_ISSUE/RECEIPT/RETURN`,V13 擴充兩個 movement_type CHECK + COUNTER 規則(全 →1320)+ WO 序號;`ItemView` 加 `reorderPoint/reorderQty`(additive,供 S6 再訂點)。V14(bill_of_materials/bom_component/work_order/work_order_component)。WIP/SCRAP/PRODUCTION_WIP 儲位、科目 1320/5930 皆既有。
  - ArchUnit:加 `manufacturing.api` 自我內聚(allowEmptyShould)+ `manufacturing` 只用 published ports;既有跨模組規則擴含 `..manufacturing..`。
  - `BillOfMaterialsTest`/`WorkOrderTest`(單元:BOM、狀態機、release 快照守衛)、`WorkOrderIssueIT`(`Dr1320/Cr1310`、raw cost state↓、consumed_value、擋超在庫)。`verify` 全綠(Surefire 54、IT 45)。
- [2026-06-27] ↩️ P3-S4 客戶退貨 / credit note(銷售沖銷)(Phase 3 Stage 4,收尾)
  - 新 `CustomerReturn`/`CustomerReturnLine`(POSTED 不可變);`CustomerReturnService.postReturn(invoiceId, stockLocationId,…)` 對「已開票未收款」發票整筆沖回:逐行 `StockPosting` SALES_RETURN CUSTOMER→STOCK(以 delivery 成本 `Dr 1330 / Cr 1340` 退庫)+ 單張 credit note JE(`Dr 4100/Dr 2400 / Cr 1200` 反收入/AR;`Dr 1340 / Cr 5100` 反 COGS);發票翻 RETURNED。REST `/api/sales/customer-returns`。V12(customer_return/line + sales_invoice 狀態 CHECK 加 RETURNED)。
  - **append-only 沖銷**:全程 INSERT 反向腿 + 反向分錄,不改既有腿/分錄。`ArSubledgerService`/`ArAgingService` 改取 live 應收(POSTED/PARTIALLY_PAID),RETURNED 不計;`SalesInvoice.applyReceipt` 加狀態守衛、`markReturned`(限未收款)。
  - `CustomerReturnIT`:整筆退後 1330/1340/5100/4100/2400/1200 跨 4 張分錄淨額全 0、庫存回補、發票 RETURNED、TB 平、AR 子帳==1200。`verify` 全綠(Surefire 45、IT 43)。
- [2026-06-27] 💵 P3-S3 收款(payments IN)+ AR 帳齡 + O2C 全鏈對帳(Phase 3 Stage 3)
  - `PaymentService.payIn`(鏡像 `payOut`):過 `Dr 1010 / Cr 1200`(1200 標 partner),逐筆經 `sales.api.ReceivableDocuments.applyReceipt` 翻發票 PARTIALLY_PAID/PAID;REST `POST /api/payments/in`。`payment.direction IN` 既有、無 schema 變更。`Allocation`(bill)/`ReceiptAllocation`(invoice)分開。
  - `sales` 加 `ArAgingService`/`ArAgingReport`(依 partner 付款條件分桶,鏡像 `ApAging*`)+ `GET /api/sales/ar-aging`。
  - ArchUnit:`payments_uses_only_published_ports` 由「禁 `..sales..`」改為「禁 `sales.domain/application/web`」(允許 `payments→sales.api`,鏡像 `purchasing.api`)。
  - `ReceiptPostingIT`(`Dr1010/Cr1200`+partner、配款翻狀態);**`ArReconciliationIT`(全鏈 O2C)**:SO→Delivery→Invoice→Receipt 後庫存↓、4100/2400/5100 入帳、1340 該訂單→0、AR(customer)→0、TB 平、AR 子帳==1200。`verify` 全綠(Surefire 45、IT 42)。
- [2026-06-26] 🧾 P3-S2 CustomerInvoice(收入 + Output VAT + 認 COGS + AR 子帳)(Phase 3 Stage 2)
  - `sales` 加 domain `SalesInvoice`/`InvoiceLine`(狀態機 DRAFT→POSTED→PARTIALLY_PAID→PAID,line 記 line_cogs);`SalesInvoiceService.postInvoice`(逐行算 net/vat 走售價,FIFO 配對 open `delivery_line` 算 COGS=配對 qty×delivery 成本 → bump qty_invoiced → 組 JE → 存 POSTED),鏡像 `VendorBillService`(無 revalue/無變異)。
  - **開票分錄**:收入側 `Dr 1200(gross,標 partner)/ Cr 4100(net)、Cr 2400(vat)`;COGS 側 `Dr 5100 / Cr 1340`(清延後 COGS)。兩側各自天然平衡,無需 9990。`ArSubledgerService.arSubledgerBalance()`=Σ 未結發票餘額,對帳到 GL 1200。
  - **published port**:`sales.api.ReceivableDocuments`/`ReceivableInvoiceView`(供 S3 payments IN 沖銷,由 `SalesInvoiceService` 實作);`DeliveryLineRepository` FIFO。REST `/api/sales/sales-invoices`。V11(sales_invoice/invoice_line)。
  - **inventory.api 增益(additive)**:`StockMovementResult` 加 `unitCost`/`value`(該次移動實際成本/金額)。修正:出貨記成本須用「出貨當下移動平均」而非 issue 後的 `newAvgUnitCost`(全數出清會歸零);Phase 4 工單成本滾算同樣需要。`DeliveryService` 改用 `result.unitCost()`。
  - `SalesInvoicePostingIT`(乾淨:`Dr1200/Cr4100/Cr2400`+`Dr5100/Cr1340`、1340 該訂單淨額→0、AR 子帳==1200;部分開票 FIFO;擋超出已出貨)。`verify` 全綠(Surefire 45、IT 39)。
- [2026-06-26] 🚚 P3-S1 sales SO→Delivery(延後 COGS 出貨)(Phase 3 Stage 1)
  - 新 `sales` 模組:domain `SalesOrder`/`SoLine`(qty_ordered/shipped/invoiced、狀態機 DRAFT→CONFIRMED→PARTIALLY_SHIPPED→SHIPPED→CLOSED)、`Delivery`/`DeliveryLine`(POSTED 不可變);`SalesOrderService`(建單/確認,驗 partner 為 customer)、`DeliveryService.deliver`(每行經 `inventory.api.StockPosting` SHIPMENT 移動 STOCK→CUSTOMER,unitCost=null 走移動平均,過 `Dr 1340 / Cr 1330`,sourceDocId 加 `#lineNo` 保 idempotency 唯一),bump qty_shipped + SO 狀態;`delivery_line.unit_cost` 記出貨成本(取 `StockMovementResult.newAvgUnitCost`);REST `/api/sales`。
  - **延後 COGS 設計**:新增資產過渡科目 `1340 Deferred COGS(已出貨未開票)`(鏡像採購側 2150 GR-IR);新 movement type `SHIPMENT`/`SALES_RETURN` + COUNTER 規則→1340;出貨只認成本到 1340,COGS 留待開票(S2)認列。V9(1340、movement type CHECK 擴充 ×2 表、COUNTER 規則、SO/DLV/INV/REC/CRN 序號)、V10(sales_order/so_line/delivery/delivery_line)。
  - ArchUnit:加 `sales.api` 自我內聚(`allowEmptyShould`,api 待 S2)+ `sales` 只用 published ports;既有跨模組規則擴含 `..sales..`。
  - `SalesOrderTest`(單元:qty rollup/狀態)、`DeliveryPostingIT`(`Dr1340/Cr1330`、FG cost state↓、qty_shipped bump、部分出貨、擋超量/超在庫)。`verify` 全綠(Surefire 45、IT 36)。
- [2026-06-26] 💸 P2-S4 payments + 配款 + AP 帳齡 + 全鏈對帳(Phase 2 Stage 4,收尾)
  - 新 `payments` 模組:`Payment`(direction IN|OUT,供 Phase 3 客戶收款重用)/`PaymentAllocation`(`document_id` 泛型,無硬 FK);`PaymentService.payOut` 過 `Dr 2100(partner)/ Cr 1010`,經 `purchasing.api.PayableDocuments.applyPayment` 配款翻帳單 PARTIALLY_PAID/PAID;單一交易只取 JE 序號鎖;REST `/api/payments`。`ApAgingService`(依 partner 付款條件分桶)+ `/api/purchasing/ap-aging`。V8(payment/payment_allocation)。ArchUnit:payments 只用 `ledger.api`+`purchasing.api`,各模組不依賴 payments。
  - `PaymentPostingIT`(Dr2100/Cr1010 + partner、配款翻狀態、配款不符擋下);**`ApReconciliationIT`(全鏈驗收)**:PO→GR→Bill→Payment 後 GR-IR→0、AP→0、AP 子帳==2100、TB 平、庫存上升。`verify` 全綠(IT 32)。
- [2026-06-26] 🧾 P2-S3 VendorBill + partnerId + revalue(Phase 2 Stage 3)
  - **ledger.api(additive)**:`JournalEntryRequest.Line` 加可選 `partnerId`(4 參數相容建構子);`JournalEntry.addLine` overload + `JournalLine` 7 參數建構子寫入既有 `partner_id` 欄;`LedgerPostingService` 傳 partnerId。Phase 0/1 零迴歸。
  - **inventory**:`StockPosting.revalue`(鎖 ItemCostState→`applyRevaluation`→append 一條 qty=0 STOCK 腿、不另過 JE;**在 bill 過 JE 前呼叫**,鎖序 ItemCostState→JE 序號一致、無死鎖;STOCK location 由 inventory 自身從現有 SLE 腿解析)。
  - **purchasing**:`VendorBill`/`BillLine`、`VendorBillService.postBill`(FIFO 配對 open grn_line 清 GR-IR、逐行 VAT、價差走 revalue、JE 借貸由會計恆等天然平衡)、`ApSubledgerService`、`purchasing.api.PayableDocuments`/`PayableBillView`(供 payments)、`GrnLineRepository`;REST `/api/purchasing/vendor-bills`;V7。ArchUnit:purchasing.api 自我內聚。
  - `VendorBillPostingIT`:乾淨帳單 `Dr2150/Dr1450/Cr2100` + partner 標記 + AP 子帳==2100;價差帳單 `Dr2150/Dr1310/Dr1450/Cr2100` + 移動平均 10→11。`verify` 全綠(IT 28)。
- [2026-06-26] 📥 P2-S2 purchasing PO→GR(Phase 2 Stage 2)
  - 新 `purchasing` 模組:domain `PurchaseOrder`/`PoLine`(qty_ordered/received/billed 追蹤、狀態機 DRAFT→CONFIRMED→PARTIALLY_RECEIVED→RECEIVED)、`GoodsReceipt`/`GrnLine`;`PurchaseOrderService`(建單/確認)、`GoodsReceiptService.receive`(每行經 `inventory.api.StockPosting` RECEIPT 移動 VENDOR→STOCK,過 Dr 1310/Cr 2150,sourceDocId 加 `#lineNo` 保 idempotency 唯一),bump qty_received + PO 狀態;REST `/api/purchasing`;V6(purchase_order/po_line/goods_receipt/grn_line)。ArchUnit:purchasing 只用 published ports + 既有 3 規則擴含 purchasing/payments。子行集合採 EAGER(GET/confirm 端點在交易外映射)。`GoodsReceiptPostingIT` 3 綠;`verify` 全綠(IT 26)。
- [2026-06-26] 🤝 P2-S1 Partner + 種子 + 稅率(Phase 2 Stage 1)
  - masterdata 加 `Partner`(code uniq、is_vendor/is_customer、payment_terms_days、ap/ar_account_code 覆寫)與 `TaxRate`(code PK、rate 19,6);`masterdata.api` 加 `PartnerView` + `MasterDataQuery.findPartner/findPartnerByCode/findTaxRate`;`MasterDataService.createPartner` + `/api/masterdata/partners` REST + `DuplicatePartnerCodeException`(409)。V5:partner/tax_rate 表、seed VENDOR+CUSTOMER 儲位於 WH1、`InventoryPostingRule` COUNTER `RECEIPT→2150`、tax_rate STANDARD 0.05、PO/GRN/BILL/PAY 序號。`verify` 全綠(IT 23;舊測試 `rejectsMovementTypeWithNoCounterRule` 改用 TRANSFER,因 RECEIPT 已有規則)。
- [2026-06-26] 📊 P1-S3 inventory 模組 + V4 + 對帳/並行測試(Stage 3,Phase 1 收尾)
  - `shared.Quantity` value object(對稱 Money,scale 6)。`inventory` 模組:append-only `StockLedgerEntry`(DB trigger 擋改/刪)、`ItemCostState` 移動加權平均(`SELECT…FOR UPDATE` + `@Version`;`ON CONFLICT DO NOTHING` 安全建列)、`StockAdjustment` 雙腿過帳(STOCK↔INVENTORY_LOSS,共用 `movement_group_id`);`StockPostingService` 在同一 `@Transactional` 內鎖 ItemCostState→算平均→append 子帳→同步 `LedgerPosting.post`(JE 序號最內層,鎖序一致無死鎖);`InventoryReportService` 對帳讀側;`/api/inventory` REST。新增 `ledger.api.SequenceAllocator`(共享編號 port)供 ADJ 文件編號。
  - **value 採 NUMERIC(19,4)(money 尺度),qty/cost 採 (19,6)** → 子帳 value_delta 與 GL 過帳同尺度,對帳精確相等(2 行調整分錄天然平衡,免 9990 殘差腿)。
  - 測試:`QuantityTest`/`ItemCostStateTest`/`ItemTest`(Surefire);`StockAdjustmentPostingIT` 4、`InventoryReconciliationIT`(驗收)、`StockPostingConcurrencyIT`(並行)。`verify` 全綠(IT 19)。ArchUnit 加 inventory/ledger 邊界 + api 自我內聚。
- [2026-06-26] 📦 P1-S2 masterdata 模組 + V3(Stage 2)
  - `masterdata` 模組:domain `Item`/`Warehouse`/`Location`/`InventoryPostingRule`;`masterdata.api`(enums `ItemType`/`LocationType`/`InventoryMovementType`/`PostingRuleRole`/`ValuationMethod`、`ItemView`/`LocationView`、`MasterDataQuery` port);`MasterDataQueryService`(讀)/`MasterDataService`(寫)+ 例外;`/api/masterdata` REST。V3:item/warehouse/location/inventory_posting_rule 表(cost NUMERIC(19,6)、enum CHECK、過帳規則用兩個 partial unique index 守 INVENTORY/COUNTER 角色)、seed WH1 倉 + 6 型別化儲位 + 過帳規則(RAW→1310/WIP→1320/FINISHED→1330、ADJUSTMENT→6000)+ STOCK_ADJUSTMENT 序號。`MasterDataQueryServiceIT` 7 綠;`verify` 全綠(IT 13)。
- [2026-06-26] 🔌 P1-S1 ledger published api port(Stage 1)
  - 把 `JournalEntryRequest` 搬到 `com.erp.ledger.api`;新增 `LedgerPosting` 介面(`post→PostingResult`)與 `PostingResult(entryId, entryNo, status)` record;`LedgerPostingAdapter` 委派既有 `LedgerPostingService.post`(回傳 `JournalEntry` 維持原樣,controller / Phase 0 IT 呼叫點不動)。ArchUnit 加 `DoNotIncludeTests`、把 domain/application/web 規則泛化到全模組、加 `ledger.api` 自我內聚規則。`verify` 全綠(Phase 0 既有 15 測試零迴歸)。
- [2026-06-26] 📄 R0 GitHub repo 上線與 CI 綠
  - 建立 public repo `q86865511/ERPSystem`;首次 push(授權直推);PR #1(修 mvnw 執行權限)squash-merge;main CI 綠燈。
- [2026-06-26] 🧪 R0 過帳測試與架構規則(15 綠)
  - Surefire:MoneyTest 5、ArchitectureTest 3、ErpApplicationTests 1。Failsafe(真 Postgres):LedgerPostingServiceIT 6 —— 平衡 / 不可過不平衡 / 期間關閉與不存在拒絕 / idempotency / DB 擋改 POSTED。
- [2026-06-26] 🖥️ R0 分錄與試算表 REST + Basic 認證
  - `POST /api/ledger/journal-entries`、`GET /api/ledger/trial-balance`;單一 ADMIN(HTTP Basic;JWT 留待 RBAC 階段)。
- [2026-06-26] 🌐 R0 DB 不變量與科目表 seed
  - Flyway V1:平衡 deferred constraint trigger、擋改/刪 POSTED(entry+line)、CHECK 借貸恰一邊、entry_no 唯一、idempotency 部分唯一索引。V2:GEN 日記帳、JE 序號、FY2026 月期間、科目表(TWD)。
- [2026-06-26] 🌐 R0 複式記帳總帳引擎
  - `shared.Money`;`ledger` 模組(Account / Journal / FiscalYear+Period / JournalEntry+Line / NumberSequence)+ repositories;`LedgerPostingService` 唯一過帳入口(期間 OPEN 檢查、序號悲觀鎖、idempotency 前置檢查)。
- [2026-06-26] 📄 R0 專案骨架與基建
  - Spring Boot 4.1 + Java 21 + PostgreSQL 16 模組化單體;docker-compose(postgres:16);GitHub Actions CI(`mvn verify`);Maven Wrapper;ADR-0001(modular monolith)。
- [2026-06-26] 📄 R0 系統設計與實作計畫定案
  - 多代理人設計工作流(6 維度 → 整合 → 對抗式審查);定案技術棧、模組化單體、移動加權平均、並行鎖序、編號、idempotency、退貨等決策。計畫檔見 `~/.claude/plans/`。

## 進行中
- **Phase 5 報表與期間結**。S1(財務報表)完成。下一棒 S2 對帳健康檢查(hero:全域借=貸、庫存子帳==GL、AR/AP 子帳==控制、GR-IR/Deferred-COGS/WIP 歸零 —— 跨模組經各 `*.api` 彙整)、S3 soft-close(期間關閉/重開 + 過帳擋下)。計畫見 `~/.claude/plans/pm-erp-enchanted-aurora.md`。

## 待辦
- **Phase 1 商品與庫存(下一棒)**:Item / Warehouse / Location 主檔、append-only `StockLedgerEntry`、移動加權平均(`ItemCostState`,`SELECT…FOR UPDATE`)、`StockAdjustment` 雙腿過帳,並建立 `ledger` 的 published `api`(供 inventory 跨模組同步過帳)。驗收:庫存帳值 == GL Inventory 控制科目餘額(對帳測試)。
- Phase 2 採購到付款 → Phase 3 訂單到收款 → Phase 4 製造(最低可展示里程碑)→ Phase 5 報表與期間結 → Phase 6 打磨與打包。詳見計畫檔。

## 已知問題
- 本機 `java`/`mvn` 不在沙箱 shell 的 PATH;建置需顯式設定 `JAVA_HOME=E:\JDK21` 並把 System32/PowerShell 路徑補進 PATH(否則 mvnw.cmd 找不到 powershell 無法 bootstrap)。
- Testcontainers 需 Docker daemon;若 `docker info` 連不上需先啟動 Docker Desktop(`C:\Program Files\Docker\Docker\Docker Desktop.exe`)。
- **教訓**:整合測試命名為 `*IT` 由 Failsafe 在 `verify` 跑,`mvn test` 不會跑到(Surefire 只跑 `*Test`/`*Tests`)。請用 `mvn verify` 跑完整測試;CI 已用 `verify`。
- ~~README CI badge 佔位~~(已解決:指向 `q86865511/ERPSystem`,main CI 綠)。

## 重要決策紀錄
- **建置策略=從零自建**:作品集價值在於展示自己的架構與 ERP 領域素養,而非 Odoo/Frappe 設定。
- **架構=模組化單體**:模組邊界=套件邊界,用 ArchUnit 在 CI 強制;跨模組過帳一律同一 `@Transactional` 內直接同步呼叫 `LedgerPostingService`,domain event 只給 audit/通知。
- **[P1] 跨模組過帳走 published port**:`ledger` 暴露 `ledger.api.LedgerPosting`(回傳精簡 `PostingResult`,不外露 `JournalEntry` 實體);以薄 `LedgerPostingAdapter` 包既有 service,讓 Phase 0 web/IT 呼叫點零改動。其他模組只依賴 `*.api`,ArchUnit 守「不碰他模組 domain/application/web」。
- **[P1] 庫存=型別化儲位的雙腿移動**:每次移動寫兩條共用 `movement_group_id` 的 `StockLedgerEntry`(STOCK 腿 ↔ 虛擬 location 腿,如 INVENTORY_LOSS),`SUM(qty/value)=0` 天然平衡;只有 STOCK 腿餵 `ItemCostState`。`StockAdjustment` 盤盈 Dr Inventory/Cr 6000、盤虧反向。
- **[P1] 精度=value 走 money 尺度(19,4)、qty/cost 走 (19,6)**:子帳 `value_delta`/`total_value` 與 GL 過帳同為 scale 4,對帳能**精確相等**;移動平均 `avg_unit_cost` 為 scale 6 導出值。full-drain(在庫歸零)精準吸收殘差使 `total_value` 歸零。2 行調整分錄天然平衡,Phase 1 不需 9990 殘差腿(留待多行過帳)。
- **[P1] 鎖序=ItemCostState 先、JE 序號最內層**:必須先鎖讀 `ItemCostState`(`SELECT…FOR UPDATE`)算出平均才能組分錄,JE 序號在 `LedgerPosting.post` 內最後取得(單一全域列、永遠最內層)→ 全域一致取得順序、無死鎖。計畫原寫「Sequence 先於 ItemCostState」物理上不可能,以「序號最內層」滿足其防死鎖意圖。首次建列用 `INSERT…ON CONFLICT DO NOTHING` 避免競態例外。
- **[P1] 負庫存阻擋**:service 層(`StockPostingService`)讀鎖定的在庫先擋,丟 `NegativeInventoryException`;domain `applyIssue` 另有防禦性 `IllegalStateException`(分層鏡像 ledger)。
- **[P1] 業務文件編號走共享 kernel port**:`StockAdjustment` 編號用 `ledger.api.SequenceAllocator`(把 `number_sequence` 表視為 ledger=module zero 的共享基建),沿用 inventory 對 `ledger.api` 的既有依賴,不另起第二套編號機制。
- **[P2] GR-IR 清算 + 輕量三方比對**:收貨過 `Dr 1310 / Cr 2150`(經 inventory `StockPosting` RECEIPT);發票 FIFO 配對已收未請 `grn_line` 清 `Dr 2150`(= Σ 配對 qty × 收貨成本 = 原始貸方),使 2150 對完整循環歸零。三方比對只記 `match_status` 不擋過帳;唯一硬不變量 = GR-IR 借方 == 配對收貨貸方。`qty_billed` 在 grn_line/po_line 當水位。
- **[P2] 採購價差→庫存(無 PPV)**:帳單價≠收貨成本時,差額入該 item 庫存控制科目(發票 JE 出 `Dr 1310`),並經 `StockPosting.revalue`(qty=0 STOCK 腿 + `applyRevaluation`)同步移動平均,維持「子帳==GL」與「快取==STOCK 腿」不變量。revalue **在發票過 JE 之前**呼叫,鎖序 `ItemCostState→JE 序號` 與收貨一致、無死鎖。`Item.valuation_method` 保留,standard-cost+PPV 為 v2 additive。
- **[P2] partner 維度走既有 hook**:`journal_line.partner_id`(Phase 0 預留)由 AP/GR-IR 行寫入;權威 AP 子帳=未結帳單 open balance(purchasing),對帳到 GL 2100。`JournalEntryRequest.Line` additive 加 partnerId(4 參數相容建構子),Phase 0/1 零改動。
- **[P2] payments 獨立模組、direction IN|OUT**:Payment+PaymentAllocation 供採購付款與 Phase 3 客戶收款共用;`PaymentAllocation.document_id` 泛型(無硬 FK)。payments 經 `purchasing.api.PayableDocuments` 沖銷帳單,不碰 purchasing internals。
- **[P3] 出貨成本走延後 COGS(deferred COGS),鏡像 GR-IR**:新增資產過渡科目 `1340`。出貨(SHIPMENT)只過 `Dr 1340 / Cr 1330`(移動平均成本),COGS 留到開票時 FIFO 配對 delivery 成本以 `Dr 5100 / Cr 1340` 認列,使「出貨↔開票」對稱清零(完整循環後 1340→0),與採購側「收貨↔請款」清 GR-IR 對稱。出貨即認 COGS 為較簡方案,但延後 COGS 展示收入/成本配比與過渡科目對稱性(使用者選定)。
- **[P3] 每業務移動一個 movement type + 一條 COUNTER 規則**:`StockPostingService` 的 counter 科目由 movement type 解析(每型唯一一條規則),方向由「哪腿是 STOCK」決定。故出貨用新 `SHIPMENT`(counter 1340)、退貨用 `SALES_RETURN`(counter 1340),不重用 `RECEIPT`/`ISSUE`(counter 會錯)。新 movement type 需同時擴充 `stock_ledger_entry` 與 `inventory_posting_rule` 兩個 `movement_type` CHECK。
- **庫存估值=移動加權平均**(MVP 不做 PPV);standard-cost + 變異列為 v2(`Item.valuation_method` 保留欄)。
- **並行=READ COMMITTED + 固定順序的悲觀鎖**(Sequence 先於 ItemCostState),不使用全域 SERIALIZABLE。
- **編號**:只有 `JournalEntry.entry_no` 連號;業務文件用 unique-monotonic + prefix。
- **Idempotency 鍵=(source_doc_type, source_doc_id, source_event)**(支援單文件多腿過帳)。
- **退貨/沖銷**:已過帳文件統一 CANCEL/REVERSE(反向庫存腿 + 反向分錄 + 翻狀態)+ 每側一種退貨文件。
- **UoM**:MVP 全系統單一基本單位(刻意切割);購買/庫存單位換算列為後期。
- **負庫存**:MVP 阻擋(移動平均下負庫存平均成本無意義)。
- **期間結**:MVP 只做 soft-close;保留盈餘動態計算,hard-close + 結轉延後。
- **認證(Phase 0)**:採 HTTP Basic + 單一記憶體 ADMIN(thin auth),而非計畫原寫的 JWT —— 依審查「Phase 0 RBAC 過度設計」建議先求精簡;JWT/細粒度 RBAC 留到安全/RBAC 階段(Phase 6)。
- **測試分層**:單元測試 `*Test`(Surefire / `test` 階段),Testcontainers 整合測試 `*IT`(Failsafe / `verify` 階段)。
- **Git 模式(本 session)**:第一次 push 時建立新 GitHub repo 並直接 push;之後自動開 PR;**merge 一律等使用者同意**。首次 push 前向使用者確認 repo 名稱與公開/私有。

## 環境備忘
- JDK:Oracle JDK 21.0.11 LTS,`JAVA_HOME=E:\JDK21`。
- 工具:Docker 29.x + Compose、Node 24、Git、`gh` 在 `E:\GithubCLI\gh.exe`。
- 建置指令(PowerShell):`$env:JAVA_HOME='E:\JDK21'; $env:Path='C:\Windows\System32;C:\Windows\System32\WindowsPowerShell\v1.0;'+$env:Path; .\mvnw.cmd -B -ntp test`
