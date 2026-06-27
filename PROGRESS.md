# PROGRESS — 製造業 ERP(作品集專案)

## 目前狀態
**🎉 Phase 7(全端化:React 前端 + 容器化 demo)全數完成** — 把「後端完整但隱形」變成可 `docker compose up` 一鍵展示的全端產品。決策:全面覆蓋 CRUD、獨立 nginx 容器、springdoc-openapi + 從 spec 產生 TS client、補唯讀列表端點、BigDecimal 序列化為字串、stack 用最新穩定(React 19 + Mantine 9 + React Router 7 + Vite 8 + TS 5.9)。計畫見 `~/.claude/plans/lovely-shimmying-iverson.md`。**Stage 1(後端 enablement + 前端骨架)完成**:後端加 springdoc 3.0.3(對應 Boot 4;`/swagger-ui.html` 上線)、`GET /api/auth/me`(SPA 登入探針 + 角色來源,放 `iam.web` 不踩 ArchUnit)、自訂 `AuthenticationEntryPoint` 抑制 `WWW-Authenticate: Basic`(免瀏覽器原生彈窗)、全域 BigDecimal→JSON 字串(`JacksonConfig`,Jackson 3 `tools.jackson.*`)+ `SpringDocUtils.replaceWithClass(BigDecimal,String)` 讓 spec 一致;`OpenApiSpecIT`(MockMvc 匯出 spec、守命名衝突與金額型別)。前端 `frontend/`(Vite + React 19 + Mantine 9 + TS):OpenAPI→TS codegen(`openapi-typescript`+`openapi-fetch`)、認證/RBAC 層、AppShell、登入頁、8 模組 placeholder 路由。`mvn verify` 全綠(Surefire 57、IT 62);`npm run build` 綠。**Stage 2(主檔 CRUD + 列表端點)完成**:後端為 masterdata 加 4 個唯讀 `GET` 列表端點(items、partners(?vendor/?customer 篩選)、warehouses、locations(?warehouseId 篩選)),並把 by-sku 查詢移到 `/items/by-sku`(避免同 path 兩個 GET 被 springdoc 合併成 `oneOf`;`OpenApiSpecIT` 加 `oneOf` 守門)。前端:分頁式 Master Data 頁(items/partners/warehouses/locations,各列表 + 角色限定 ADMIN 的建立),可複用 `EntitySelect`(Item/Partner/Warehouse/Location 選擇器,供 S4–S7)、TanStack Query hooks、共用元件。`mvn verify` 全綠(Surefire 56、IT 66);`npm run build` 綠。**Stage 3(儀表板/財報/對帳 hero)完成**(純前端,唯一後端改動為修 spec 命名衝突):Dashboard 對帳健康檢查 hero(healthy、子帳 vs GL、過渡科目歸零)+ 摘要;Reports 頁(試算表→點科目開總帳鑽取 Drawer、損益表、資產負債表,共用 as-of 日期);Inventory 頁(在庫查詢 + 子帳對帳)。修正 `Line` 靜默命名衝突(`TrialBalance.Line` 與 `JournalEntryRequest.Line` 都叫 `Line` → 合併成一個 schema、`TrialBalance.lines` 被錯誤型別化;以 `@Schema(name=TrialBalanceRow/JournalEntryLine)` 拆開),`OpenApiSpecIT` 加守門。`mvn verify` 全綠(Surefire 56、IT 66);`npm run build` 綠。**Stage 4(採購 P2P)完成**:後端為 PO/GR/Bill/Payment 加唯讀列表端點(`GET /api/purchasing/{purchase-orders,goods-receipts,vendor-bills}`、`GET /api/payments?direction=`)+ `PurchasingListIT`。前端 `/purchasing`(Tabs:採購單/收貨/廠商帳單/付款/AP 帳齡)—— PO 多行建單 + 確認、GR 對 PO 部分收貨、Bill 對 PO 開票(顯示行級 FIFO matchStatus + 稅/總額)、對外付款(選廠商 → 配款至未結帳單,金額以 BigInt 精確加總)、AP 帳齡。`mvn verify` 全綠(Surefire 56、IT 67);`npm run build` 綠。**Stage 5(銷售 O2C)完成**:後端加 SO/Delivery/Invoice/Return 列表端點;前端 `/sales`(SO、出貨、發票(顯示 COGS)、收款、退貨 credit note 雙帳、AR 帳齡)。IT 68。**Stage 6(製造)完成 → 🎉 買→做→賣前端全齊**:後端加 BOM/WO 列表端點;前端 `/manufacturing`(BOM 建單、工單狀態機 release/issue/complete/cancel、再訂點)。IT 69。**Stage 7(進階)完成**(純前端、後端不變):Inventory 加 Adjustments 分頁、新 `/ledger` 頁(手動分錄 + 即時借貸平衡、期間關閉/重開);8 個導覽模組皆有實頁,placeholder 全移除。`npm run build` 綠。**Stage 8(容器化 + 收尾)完成 → 🎉 Phase 7 全數完成**:後端 multi-stage `Dockerfile`(`jarmode=tools` 分層、`-DskipTests`、Windows mvnw CRLF/+x 修正)、前端 `frontend/Dockerfile`(node build → nginx)、`frontend/nginx/nginx.conf`(SPA fallback + 反代 `/api`、`/v3/api-docs`、`/swagger-ui` 到 `app:8080`)、`compose.demo.yaml`(postgres+volume+healthcheck → app(`SPRING_DOCKER_COMPOSE_ENABLED=false`、`SPRING_PROFILES_ACTIVE=seed`、actuator health)→ frontend `8081:80`)、`.dockerignore`(保留 `maven-wrapper.properties`)。`docker compose -f compose.demo.yaml build` 兩個 image(app 528MB / frontend 75MB)build 成功(沙箱擋 loopback,實際 `up` 由使用者機器驗證)。CI 加前端 job(`npm ci && npm run build`)。**雙語 README**(`README.md` 繁中為主 + `README.en.md` 英文,套用 Soulshard-Hunter 風格:雙語標題/徽章/TOC/emoji 標題/技術亮點/快速開始/已知限制/文件索引)。一鍵 `docker compose -f compose.demo.yaml up --build` 即可展示 買→做→賣 + 對帳 hero。

**Phase 3(訂單到收款)完成** — 分 4 段 PR 交付(SO→Delivery、CustomerInvoice、Receipt+AR 帳齡、CustomerReturn)。採「延後 COGS(deferred COGS)」鏡像 GR-IR:出貨成本停在過渡科目 1340,開票才認 COGS,「出貨↔開票」對稱清零。**O2C 驗收達成(`ArReconciliationIT`)**:全鏈跑完庫存↓、COGS+收入+Output VAT 入帳、1340→0、AR→0、AR 子帳==1200、試算表平衡;客戶退貨(credit note)全鏈沖回零。`mvn verify` 全綠(Surefire 45、IT 43)。**Phase 6(打磨與打包)完成** — 分 3 段 PR(RBAC、demo seed、README 收尾)。RBAC 4 角色(URL 授權 + 4 in-memory 使用者,ADR-0008);一鍵 demo seed(`DataSeeder`,profile `seed`)經真實過帳 service 灌完整 買→做→賣;README 收尾(模組對照表 + 各 published port、Mermaid ERD、ADR 索引、角色/seed 用法、roadmap 全標完成)。`mvn verify` 全綠(Surefire 55、IT 58)。**🎉 Phase 0–6 全數完成**(完整路線圖落地;19→24 個 PR、IT 58、CI 綠)。

**Phase 5(報表與期間結)完成** — 分 3 段 PR(財務報表、對帳健康檢查、soft-close)。`reporting` read-side 模組:財務報表(試算表 as-of、資產負債表保留盈餘動態、損益表、總帳 drill-down)+ **對帳健康檢查(hero,`/api/reporting/reconciliation`)**:全域 TB 平 + 庫存/AP/AR 子帳==GL 控制科目(跨模組經各 `*.api`),過渡科目餘額一併呈現。**soft-close**:`/api/ledger/fiscal-years/{year}/periods/{n}/close|reopen`,關閉後該期間過帳被擋。`mvn verify` 全綠(Surefire 55、IT 52)。下一棒(待指示):Phase 6 打磨與打包(RBAC 補 4 角色、一鍵 demo seed、README/架構圖收尾)。

**Phase 4 製造完成** —— 分 3 段 PR(BOM+release+領料、完工成本滾算+再訂點+對帳、工單取消)。`manufacturing` 模組 BOM→WorkOrder(release 快照)→領料(`Dr 1320 / Cr 1310`)→完工(成本滾算 `Dr 1330 / Cr 1320`,餘差掃 5930)→可取消(反向領料 `Dr 1310 / Cr 1320`)。**製造驗收達成(`MfgReconciliationIT`)**:原料↓、成品依滾算成本↑、**WIP(1320)→0**、庫存子帳==GL、試算表平衡。再訂點報表上線(經新 `inventory.api.InventoryQuery`)。**🎉 路線圖「買→做→賣」最低可展示里程碑(Phase 4 結束)達成。** `mvn verify` 全綠(Surefire 54、IT 49)。下一棒(待指示):Phase 5 報表與期間結。
**Phase 2(採購到付款)完成** — 分 4 段 PR 交付(Partner/種子、purchasing PO→GR、VendorBill、payments)。**Phase 2 驗收達成(`ApReconciliationIT`)**:PO→GR→VendorBill→Payment 全鏈跑完後 **GR-IR→0、AP→0、AP 子帳==GL 2100、試算表平衡、庫存上升**;採購價差走庫存重估、GL 帶 partner 維度。
Phase 1(商品與庫存)已完成:`inventory` 移動加權平均、append-only 子帳、對帳達成「庫存帳值==GL」。Phase 2 計畫見 `~/.claude/plans/phase-1-iridescent-ember.md`,總路線圖見 `~/.claude/plans/pm-erp-enchanted-aurora.md`。

## 已完成
- [2026-06-27] 🐳 P7-S8 容器化 demo + 雙語 README(Phase 7 Stage 8,Phase 7 收尾)
  - **容器化**:後端 `Dockerfile`(multi-stage:`temurin:21-jdk-jammy` 用 mvnw `-DskipTests` build → `jarmode=tools extract --layers` 分層 → `temurin:21-jre-jammy` 非 root + curl;Windows mvnw `sed CRLF` + chmod)。`frontend/Dockerfile`(`node:22-slim` `npm ci` + `vite build` → `nginx:1.27-alpine`)。`frontend/nginx/nginx.conf`(SPA `try_files` + 反代 `/api`、`/v3/api-docs`、`/swagger-ui`、`/webjars` 到 `app:8080`,`proxy_pass` 不帶尾斜線保留路徑)。`compose.demo.yaml`(獨立於既有 `compose.yaml`:postgres+named volume+`pg_isready` healthcheck → app(`SPRING_DATASOURCE_*`、`SPRING_PROFILES_ACTIVE=seed`、`SPRING_DOCKER_COMPOSE_ENABLED=false`、actuator health、`start_period 60s`)→ frontend `8081:80`)。`.dockerignore`(根與 frontend;根保留 `.mvn/wrapper/maven-wrapper.properties`)。`DataSeeder` 確認冪等(VEND-DEMO 已存在即跳過),故保留 volume 重跑安全。`docker compose -f compose.demo.yaml build` 兩 image(app 528MB / frontend 75MB)build 成功。**修正**:後端 image 原用 `jarmode=tools` 分層 + 舊版 `JarLauncher` ENTRYPOINT → 容器 `ClassNotFoundException`,改為直接 `java -jar app.jar`(fat jar)。**在 Oracle Cloud(ARM64,`ssh oracle`)端到端實測通過**:三容器 healthy、`/api/auth/me` 回角色、**對帳 `healthy:true`**(seed 後帳對平)、BigDecimal 為字串、Swagger 200、未認證 401 無 WWW-Authenticate(見 [[oracle-deploy-target]] 記憶)。
  - **CI**:`.github/workflows/ci.yml` 加 `frontend` job(`actions/setup-node@v4` Node 22 + `npm ci` + `npm run build`),原 job 改名 `backend`。
  - **雙語 README**:`README.md`(繁中為主,預設顯示)+ `README.en.md`(英文),互相切換連結;套用使用者另一 repo Soulshard-Hunter 的風格 —— 雙語標題、shields.io 徽章列、目錄、emoji 章節標題、✨技術亮點、🚀快速開始(docker demo + 本機開發)、🧪測試、🖥️前端、📊資料模型 ERD、🗺️路線圖、⚠️刻意切割、📐ADR 索引、文件索引。
- [2026-06-27] 🧮 P7-S7 進階:庫存調整 / 手動分錄 / 期間結(Phase 7 Stage 7;純前端)
  - **後端不變**(全用既有端點),`mvn verify` 仍綠(Surefire 56、IT 69)。
  - **前端**:`InventoryPage` 改 Tabs(Overview + **Adjustments** —— `AdjustmentsPanel` 庫存調整建單:ItemSelect/LocationSelect(STOCK)/qtyDelta(可負)/unitCost/reason,WAREHOUSE 限定,送出顯示 adjustmentNumber + JE)。新 `features/ledger`:`LedgerPage`(Tabs)= `ManualEntryPanel`(多行借貸 + 即時平衡檢核(`sumMoney` 比 debit/credit)+ 科目選單取自試算表 + ≥2 行且平衡才可送、送出顯示 entryNo,ACCOUNTANT)、`FiscalPeriodsPanel`(yearCode + periodNo 關閉/重開,顯示回傳期間狀態,ACCOUNTANT)。新 hooks `inventory.useCreateAdjustment`、`ledger.useCreateJournalEntry/useClosePeriod/useReopenPeriod`。`/ledger` 路由啟用,移除 `PlaceholderPage`(8 模組全有實頁)。`npm run build` 綠。
- [2026-06-27] 🏭 P7-S6 製造前端 + 列表端點(Phase 7 Stage 6;買→做→賣前端全齊)
  - **後端**:BOM/WO 加唯讀 `GET` 列表端點(`BomService.listBoms`/`WorkOrderService.listWorkOrders`)。`ManufacturingListIT`。`mvn verify` 全綠(Surefire 56、IT 69)。
  - **前端**(`/manufacturing`,Tabs):`BomsPanel`(parent FINISHED item + 動態多元件(ItemSelect/qtyPer/scrapPct)建單;詳情顯示元件表)、`WorkOrdersPanel`(建單選 item+BOM(依 parentItemId 過濾)+qty;詳情 Drawer **狀態機按鈕** —— DRAFT→Release、RELEASED→Issue、IN_PROGRESS→Complete、RELEASED/IN_PROGRESS→Cancel,按鈕依 status 條件 disable;issue/complete/cancel 開彈窗收 location/date(complete 另收 qtyProduced);元件表顯示 planned/consumed/value + JE)、`ReorderReportPanel`(再訂點)。`npm run build` 綠。
- [2026-06-27] 🧾 P7-S5 銷售 O2C 前端 + 列表端點(Phase 7 Stage 5)
  - **後端**:SO/Delivery/Invoice/Return 加唯讀 `GET` 列表端點(各 service `list*` + `findAll(Sort by id desc)`)。`SalesListIT`(建 SO → `listOrders` 含之)。`mvn verify` 全綠(Surefire 56、IT 68)。
  - **前端**(`/sales`,Tabs):`SalesOrdersPanel`(PartnerSelect(customer) + 動態多行建單 + 確認;詳情 ordered/shipped/invoiced)、`DeliveriesPanel`(選 confirmed SO 部分出貨;詳情顯示出貨成本 + JE,Deferred-COGS 提示)、`SalesInvoicesPanel`(對 SO 開票、預填 shippable;詳情顯示 goods/vat/gross/**COGS** + 行級 lineCogs + JE)、`PaymentsInPanel`(選客戶配款至未結發票,`sumMoney` 精確加總)、`CustomerReturnsPanel`(選 POSTED 發票出 credit note 整筆退;詳情顯示 creditNoteJournalEntryId + 行級 stockJournalEntryId,呈現「貸項分錄 + 庫存回補」雙帳)、`ArAgingPanel`。複用 S4 的 `EntitySelect`/`StatusBadge`/`sumMoney`/付款面板模式。`npm run build` 綠。
- [2026-06-27] 🛒 P7-S4 採購 P2P 前端 + 列表端點(Phase 7 Stage 4)
  - **後端**:PO/GR/Bill 加唯讀 `GET` 列表端點(`PurchaseOrderService.listOrders`/`GoodsReceiptService.listReceipts`/`VendorBillService.listBills`,`findAll(Sort by id desc)`);payments 加 `GET /api/payments`(`?direction=IN|OUT`,`PaymentRepository.findByDirection(dir, Sort)`)。`PurchasingListIT`(建 PO → `listOrders` 含之)。`mvn verify` 全綠(Surefire 56、IT 67)。
  - **前端**(`/purchasing`,Tabs):`PurchaseOrdersPanel`(PartnerSelect(vendor) + 動態多行 ItemSelect/qty/price 建單 → 確認;詳情 Drawer 顯示 ordered/received/billed 三段量)、`GoodsReceiptsPanel`(選 confirmed PO → 帶出行、輸入本次收量做部分收貨;詳情顯示 unitCost + JE)、`VendorBillsPanel`(選 PO → 預填 billable qty/price、可改價走價差;詳情顯示 goods/vat/gross + 行級 matchStatus badge + openBalance + JE)、`PaymentsOutPanel`(選廠商 → 列未結帳單配款,金額以 `sumMoney` BigInt 精確加總避免 float)、`ApAgingPanel`(as-of 帳齡桶)。共用 `StatusBadge`、`Money.sumMoney`、`usePartnerMap`/`useItemMap`。`npm run build` 綠。**Stage 5(銷售 O2C)完成**:後端為 SO/Delivery/Invoice/Return 加唯讀列表端點 + `SalesListIT`。前端 `/sales`(Tabs:銷售單/出貨/發票/收款/退貨/AR 帳齡)—— SO 多行建單+確認、Delivery 對 SO 部分出貨(Deferred-COGS)、Invoice 對 SO 開票(顯示 COGS + 行級 lineCogs)、收款(對未結發票配款,複用 `sumMoney`)、客戶退貨(選發票出 credit note、詳情顯示貸項分錄 + 行級庫存回補 stockJournalEntryId)、AR 帳齡。`mvn verify` 全綠(Surefire 56、IT 68);`npm run build` 綠。**Stage 6(製造)完成 → 🎉 買→做→賣前端全齊**:後端為 BOM/WO 加列表端點 + `ManufacturingListIT`。前端 `/manufacturing`(Tabs:工單/BOM/再訂點)—— BOM 多元件建單 + 詳情、**工單狀態機**(release/issue/complete/cancel 按鈕依 status 條件啟用,issue/complete/cancel 用彈窗收 location/date/qtyProduced;詳情顯示 planned/consumed/value + JE)、再訂點報表。`mvn verify` 全綠(Surefire 56、IT 69);`npm run build` 綠。
- [2026-06-27] 📊 P7-S3 儀表板 + 財報 + 對帳 hero(Phase 7 Stage 3)
  - **後端(僅修 spec)**:修 `Line` 靜默命名衝突 —— `reporting.TrialBalance.Line`(code/name/accountClass/debit/credit)與 `ledger.api.JournalEntryRequest.Line`(accountCode/debit/credit/memo/partnerId)同名,springdoc 把兩者合併成單一 `Line` schema,使 `TrialBalance.lines` 被錯誤型別化。以 `@Schema(name="TrialBalanceRow")` / `@Schema(name="JournalEntryLine")` 拆開。`OpenApiSpecIT` 加守門:檢查 `TrialBalance.lines` 指向的 row schema 含 `code`/`accountClass`(`_1` 後綴檢查抓不到「靜默合併」)。`mvn verify` 全綠(Surefire 56、IT 66)。
  - **前端(read-only,展示效果最強的一段)**:`ReconciliationHero`(對帳健康檢查大卡:`healthy` 綠/紅、試算表平否、各子帳 vs GL 控制科目表、過渡科目 GR-IR/Deferred-COGS/WIP 是否歸零)。`features/reporting`:`ReportsPage`(Tabs:試算表/損益表/資產負債表 + 共用 as-of `DateInput`),`TrialBalancePanel`(點科目列開 `GeneralLedgerDrawer` 鑽取該科目 `LedgerLineView`,含 sourceDoc 標記)、`IncomeStatementPanel`、`BalanceSheetPanel`(`StatementSection` 共用)。`features/inventory`:`InventoryPage`(`ItemSelect` 在庫查詢 + 子帳對帳)。Dashboard 升級為 hero + 摘要(總資產/總負債/淨利)。`GeneralLedgerDrawer` 為可複用鑽取基建(供 S4–S7 文件詳情)。`npm run build` 綠。
- [2026-06-27] 🧱 P7-S2 主檔 CRUD + 唯讀列表端點(Phase 7 Stage 2)
  - **後端**:masterdata 加 4 個 `GET` 列表端點 —— `/api/masterdata/items`、`/partners`(`?vendor`/`?customer` 篩選)、`/warehouses`、`/locations`(`?warehouseId` 篩選);`MasterDataService.list*`(`findAll(Sort)` + 衍生 finder)、`PartnerRepository.findByVendorTrue/ByCustomerTrueOrderByCode`、`LocationRepository.findByWarehouseIdOrderByCode`。**修正**:by-sku 查詢從 `/items?sku=` 移到 `/items/by-sku`,避免同 path 兩個 GET 被 springdoc 合併成 `required sku + oneOf` 的壞 operation;`OpenApiSpecIT` 加「spec 不得含 `oneOf`」守門。`MasterDataListIT`(list 含建立項、partner 篩選分流、location 依倉過濾)。`mvn verify` 全綠(Surefire 56、IT 66)。
  - **前端**:分頁式 `MasterDataPage`(Items/Partners/Warehouses/Locations)。各 panel = 列表表格 + 角色限定(ADMIN,`canDo('masterdata.create')`)的建立 Modal 表單;Locations 有倉別篩選。可複用 `components/EntitySelect`(`ItemSelect`/`PartnerSelect`/`WarehouseSelect`/`LocationSelect`,供 S4–S7 文件多行表單)。資料層 `features/masterdata/api.ts`(TanStack Query hooks)、`api/queryKeys.ts`、`api/types.ts`(DTO 別名)、共用 `PageHeader`/`Money`(BigDecimal 字串格式化,不碰 float)/`lib/notify`(解析 ProblemDetail)。`npm run build` 綠。
- [2026-06-27] 🌐 P7-S1 後端 API enablement + React 前端骨架(Phase 7 Stage 1)
  - **後端**:`pom.xml` 加 `springdoc-openapi-starter-webmvc-ui` 3.0.3(實測:Maven Central 最新 3.0.3,parent=Boot 4.0.5,屬 Boot 4/Spring 7 線;2.8.x 是 Boot 3.5 不相容)。`application.properties` 設 docs 路徑 + `writer-with-order-by-keys=true`(spec 穩定)。`config/JacksonConfig`:全域 BigDecimal→JSON 字串(Jackson 3 `tools.jackson.databind`,`SimpleModule.addSerializer(new ToStringSerializer(BigDecimal.class))`,Spring Boot 4 自動收集 `JacksonModule` bean)。`config/OpenApiConfig`:basicAuth scheme + `SpringDocUtils.replaceWithClass(BigDecimal,String)`(讓 spec 也標 string,與 runtime 一致)。`iam/web/AuthController`:`GET /api/auth/me` 回 `{username, roles[]}`(去 ROLE_ 前綴)。`SecurityConfig`:放行 `/v3/api-docs`、`/swagger-ui`、`/webjars`,換自訂 `AuthenticationEntryPoint`(401 但不送 `WWW-Authenticate: Basic`,免瀏覽器原生彈窗)。
  - **測試**:`AuthControllerIT`(未認證 401、admin 4 角色、sales 單角色)、`JacksonConfigTest`(BigDecimal→帶尺度字串)、`OpenApiSpecIT`(MockMvc 取 `/v3/api-docs`:OpenAPI 3.1、74 schemas、50 paths、**無 `_1` 命名衝突**、**無 `type:number`**、`grossAmount` 為 string;匯出 `target/openapi.json`)。`mvn verify` 全綠(Surefire 57、IT 62)。
  - **前端**(`frontend/`,獨立 Vite 專案,不掛 Maven):React 19 + Mantine 9 + React Router 7 + Vite 8 + TS 5.9(注:TS 6 與 openapi-typescript 7 peer 衝突,故用 5.9 最新)。資料層 `openapi-typescript`(產 `src/api/schema.d.ts`,commit)+ `openapi-fetch`(typed client + 認證 middleware 注入 Basic、攔 401/403)+ TanStack Query v5;認證/RBAC(`credentials`/`roles` 鏡像 SecurityConfig/`AuthContext`/`RequireAuth`/`RequireRole`)、`AppLayout`(AppShell + 導覽 + 使用者選單)、`LoginPage`(含 4 個 demo 帳號快速登入)、`DashboardPage` + 8 模組 placeholder 路由。`npm run build`(`tsc -b && vite build`)綠。
  - 環境限制:本機沙箱/VM 擋 Tomcat loopback(`SocketException: Invalid argument`),無法在此跑真實 web server / Vite dev server;故 spec 改用 MockMvc 匯出、前端僅以 `vite build` 型別檢查驗證,實機點擊驗證留待 Stage 8 的 Docker demo。
- [2026-06-27] 📘 P6-S3 README/文件收尾(Phase 6 Stage 3,Phase 6 收尾)
  - README:架構段補齊全模組(sales/manufacturing/reporting/iam)+ 「模組 × 責任 × published port」對照表;`auth` 列改 HTTP Basic 4 角色;**Data model** 段加 Mermaid ERD(會計脊椎 + 各文件/庫存子帳);**ADR 索引**(0001–0008);Running 段補 seed profile 指令與角色帳號;roadmap 全段標完成。純文件變更,build 不受影響。
- [2026-06-27] 🌱 P6-S2 一鍵 demo seed(Phase 6 Stage 2)
  - `com.erp.bootstrap.DataSeeder`(`@Profile("seed")`,ApplicationRunner):啟動時經**真實過帳 service**(非繞過不變量的原生 SQL)灌入完整 買→做→賣 —— PO→GR→Bill→付款、BOM→WO→領料→完工、SO→出貨→開票→收款;落地後 GR-IR/AP/Deferred-COGS/AR/WIP 全歸零、庫存/COGS/收入入帳。放 composition root(非業務模組,不受模組邊界規則限制),做 idempotency 防呆(VEND-DEMO 已存在則跳過)。
  - `SeedDataIT`(`@ActiveProfiles("seed")`:RM-DEMO 在庫 50、FG-DEMO 在庫 20、`reconcile().healthy()` true)。`verify` 全綠(Surefire 55、IT 58)。
- [2026-06-27] 🔐 P6-S1 RBAC 4 角色(Phase 6 Stage 1)
  - `SecurityConfig`:4 角色 ADMIN/ACCOUNTANT/WAREHOUSE/SALES,以 `authorizeHttpRequests`(URL+HTTP method)授權 —— 財務過帳(bill/invoice/payment/JE/期間結)→ ACCOUNTANT、實體移動(GR/delivery/stock-adj/製造)→ WAREHOUSE、SO → SALES、masterdata → ADMIN;GET/報表只需認證。4 個 in-memory 使用者(admin 持全角色為超級使用者,免角色階層)。延續 Phase 0 HTTP Basic;JWT/持久化使用者庫延後。
  - **設計權衡**:RBAC 放在 web 邊界(單一 REST 入口)而非 service 層 `@PreAuthorize`,避免 ~50 個 service 直呼的 IT 全要塞安全 context;ADR-0008 記錄(非 REST 入口出現時再加 method security)。
  - `RbacIT`(MockMvc 走真實過濾鏈,免綁真 socket:未認證→401、錯角色→403、對角色→通過授權)。`verify` 全綠(Surefire 55、IT 57)。
- [2026-06-27] 🔒 P5-S3 soft-close 期間關閉/重開(Phase 5 Stage 3,Phase 5 收尾)
  - `FiscalPeriodService.close/reopen(yearCode, periodNo)`(`FiscalPeriod` 既有 `close()/reopen()`、`FiscalPeriodStatus OPEN/CLOSED/LOCKED`;過帳路徑既有 `isOpen()` 檢查 → `PeriodNotOpenException`);新 `FiscalYearRepository.findByCode`、`FiscalPeriodRepository.findByFiscalYearIdAndPeriodNo`。REST `/api/ledger/fiscal-years/{yearCode}/periods/{periodNo}/{close|reopen}`。hard-close(LOCKED)+ 保留盈餘結轉仍延後。
  - `FiscalPeriodSoftCloseIT`(關閉 3 月 → 該期過帳 `PeriodNotOpenException` → 重開 → 過帳成功;用 3 月避免汙染他測試的 6 月)。`verify` 全綠(Surefire 55、IT 52)。
- [2026-06-27] ✅ P5-S2 對帳健康檢查(hero artifact)(Phase 5 Stage 2)
  - `reporting.ReconciliationService` + `ReconciliationReport`:`/api/reporting/reconciliation` 彙整全域 TB 平 + 子帳==GL 控制(庫存 1310/1320/1330、AP 2100、AR 1200),`healthy` = TB 平 ∧ 所有子帳對齊;過渡科目 GR-IR(2150)/Deferred-COGS(1340)/WIP(1320)餘額一併列出(完整循環後為 0)。
  - **跨模組經 published ports**:新 `inventory.api.InventoryQuery.subledgerByAccount()`/`InventoryAccountBalance`、`purchasing.api.PayablesQuery`(由 `ApSubledgerService` 實作)、`sales.api.ReceivablesQuery`(由 `ArSubledgerService` 實作);reporting 只依賴各 `*.api`。
  - `ReconciliationIT`(跑 P2P 後 `healthy`、TB 平、AP 子帳==2100、含庫存檢查與過渡科目列)。`verify` 全綠(Surefire 55、IT 51)。
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
- **🎉 Phase 7(全端化)Stage 1–8 全數完成** —— 全部 8 個模組皆有完整 React 前端,一鍵 `docker compose -f compose.demo.yaml up --build` 起 postgres + 自動 seed 後端 + 前端;雙語 README。每 stage 一個 PR。Stage 8 PR 待 merge。**可選後續**:在使用者機器上實跑 `up` 驗收 + 補前端畫面 screenshots(`docs/`);JWT/持久化使用者庫;雲端部署;前端 bundle code-split(@tabler/icons barrel)。
- **基底 Phase 0–6 全數完成** —— 總帳 / 庫存 / 採購到付款 / 訂單到收款 / 製造 / 報表與期間結 / 打磨與打包,完整路線圖落地,CI 綠。

## 待辦
> Phase 0–7 已全部完成(後端 + 全端 React 前端 + 一鍵 demo + 雲端上線 https://erp.terrychou.com)。以下為尚未做的後續,依優先序。

**A. 收尾 / 驗證(高優先)**
- ✅ **前端實機點測(已做)**:用 **headless Playwright** 對 live demo(Oracle)自動點測 —— admin 登入 → 8 頁全部渲染(Dashboard/Master Data/Purchasing/Sales/Manufacturing/Inventory/Reports/Ledger)+ 開「New item」表單 Modal,**零 console / pageerror / 失敗 /api 請求**;對帳 hero 綠、PO-000001 列表帶 partner 名與 RECEIVED 狀態徽章。互動式逐流程(實際送 PO→GR→Bill…)仍可再深測,但渲染 + 主要互動已過。
- ✅ **README 截圖(已做)**:Playwright 截 8 頁 + Modal 放 `docs/screenshots/`,README(繁/英)嵌入儀表板 hero + 採購/製造/財報。
- **前端 bundle 瘦身**:`@tabler/icons` barrel 使 JS >800KB(build 警告);改 per-icon import 或 code-split。

**B. 前端中/英 i18n 切換(使用者要求,新)**
- 目前前端 UI 全英文。需加 **中文/英文 語言切換**(react-i18next 或輕量 context;抽出全部 UI 字串為 zh/en;AppShell header 放切換器;偏好存 localStorage)。與雙語 README 對齊。規模約一個 stage。**決定**:預設語言**跟隨瀏覽器**(zh-* → 繁中,其餘 → 英文),使用者可手動覆寫。(使用者選定先實機點測再做 i18n。)

**C. 安全**
- JWT + 持久化使用者/角色庫(取代 HTTP Basic + 4 in-memory;前端 auth 層已抽象化好接)。
- live demo 為 `admin/admin` 公開可寫;可考慮唯讀帳號/唯讀模式(後端強制借貸平衡 + 子帳對帳,對帳 hero 恆綠,訪客動不壞不變量,但會累積測試資料)。

**D. 財會加值**
- PDF/列印報表(發票/PO/出貨單/試算表)、獨立 append-only `audit_log` + 敏感動作事件監聽、hard-close + 保留盈餘年結轉(目前只 soft-close,保留盈餘動態算)。

**E. 品質 / 維運**
- 並行測試擴到 sales/manufacturing(目前僅 inventory 有 concurrency IT);**前端無自動化測試**(只有 tsc + vite build,可加 Vitest/Playwright)。
- 自動部署(merge 後 GitHub Actions 自動 pull + rebuild;目前 Oracle 上手動 clone + compose up);demo 定時重置(cron 每晚 `down -v && up` 還原乾淨 seed)。

**F. 刻意切割(YAGNI,不打算做)**
- 多幣別/FX、多公司/多租戶、FIFO/標準成本+PPV、多稅率稅引擎、簽核流程、時間相位 MRP、批號/序號、工序/工作中心/人工製費、多倉調撥、多階 BOM、購買↔庫存單位換算(UoM)。

## 已知問題
- 本機 `java`/`mvn` 不在沙箱 shell 的 PATH;建置需顯式設定 `JAVA_HOME=E:\JDK21` 並把 System32/PowerShell 路徑補進 PATH(否則 mvnw.cmd 找不到 powershell 無法 bootstrap)。
- Testcontainers 需 Docker daemon;若 `docker info` 連不上需先啟動 Docker Desktop(`C:\Program Files\Docker\Docker\Docker Desktop.exe`)。
- **教訓**:整合測試命名為 `*IT` 由 Failsafe 在 `verify` 跑,`mvn test` 不會跑到(Surefire 只跑 `*Test`/`*Tests`)。請用 `mvn verify` 跑完整測試;CI 已用 `verify`。
- ~~README CI badge 佔位~~(已解決:指向 `q86865511/ERPSystem`,main CI 綠)。
- **沙箱限制**:本機沙箱/VM 擋 Tomcat loopback,無法在此跑真實 web server / Vite dev server;故 spec 以 MockMvc 匯出、前端以 `vite build` 驗證,實機驗證改在 **Oracle**(`ssh oracle`)上跑 Docker demo。
- **前端小限制(可補可不補)**:庫存調整無歷史列表(後端無 adjustments list 端點);手動分錄科目選單取自試算表(空帳本無選項);期間關閉 `yearCode` 需手填;文件詳情點 `journalEntryId` 是導到該科目總帳鑽取的近似(無 by-entry 取整張分錄端點);主檔只有建立 + 列表,無編輯/刪除;列表端點無分頁(demo 規模夠用)。

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
- **[P4] 製造 WIP=清算控制科目、成品走實際成本滾算**:領料 `Dr 1320 / Cr 1310`(原料移動平均)累計 consumed_value;完工 `rolledCost = Σconsumed / qtyProduced`(6dp)、`Dr 1330 / Cr 1320`,殘差掃 `5930 製造變異` 使 WIP 在完整循環後歸零(整數情境餘差天然為 0)。每業務移動專屬 movement type:`MANUFACTURING_ISSUE`/`MANUFACTURING_RECEIPT`/`MANUFACTURING_RETURN`(counter 皆 1320,借方科目由 item type 區分)。標準成本+變異會計、工時/製費為延後 v2。ADR-0007。
- **[P4] 工單 release 快照 BOM、取消走 append-only 反向領料**:release 時把 BOM 展開量(`qtyPer×qty/outputQty`)凍結到 `work_order_component`(不可變原則);取消未完工工單以 `MANUFACTURING_RETURN`(WIP→STOCK,原領料成本)反向領料、WIP 歸零,不改既有腿/分錄。
- **[P5] reporting=read-side leaf、跨模組對帳只走 published ports**:財務報表(試算表/資產負債表/損益表,保留盈餘動態算)與**對帳健康檢查**(`/api/reporting/reconciliation`)由新 `reporting` 模組組合各模組 `*.api`——`ledger.api.GeneralLedgerQuery`、`inventory.api.InventoryQuery.subledgerByAccount`、`purchasing.api.PayablesQuery`、`sales.api.ReceivablesQuery`;`reporting` 不被任何模組依賴(ArchUnit 擴含 `..reporting..`)。`AccountBalance.naturalBalance()` 依 normal balance 帶號。
- **[P5] 期間只做 soft-close**:`FiscalPeriod.close/reopen`(OPEN↔CLOSED),過帳路徑既有 `isOpen()` 檢查擋下非 OPEN 期間(`PeriodNotOpenException`);hard-close(`LOCKED`,連沖銷都擋)與保留盈餘年結轉延後(資產負債表保留盈餘=Σ收入−費用 動態計算,規避 year-end close)。
- **[P6] RBAC 放 web 邊界(URL 授權),非 service `@PreAuthorize`**:`SecurityConfig` 以 `authorizeHttpRequests`(URL+HTTP method)管控 4 角色(ADMIN/ACCOUNTANT/WAREHOUSE/SALES);單一 REST 入口下與 service 層守衛等價,且可避免 ~50 個 service 直呼的 IT 全要塞安全 context。4 個 in-memory 使用者(admin 持全角色為超級使用者,免角色階層),延續 HTTP Basic;JWT/持久化使用者庫延後。ADR-0008。
- **[P6] 一鍵 demo seed 放 composition root**:`com.erp.bootstrap.DataSeeder`(`@Profile("seed")`)經**真實過帳 service**(非繞過不變量的原生 SQL)灌入完整 買→做→賣;因跨模組編排而置於 root(非業務模組,不受模組邊界規則限制),`VEND-DEMO` 已存在則跳過(idempotent)。
- **[P7] springdoc 版本=3.0.3**:Spring Boot 4.1 須用 springdoc `3.0.x`(parent=Boot 4.0.5,Spring 7 線);`2.8.x` 綁 Boot 3.5 不相容。先前 solrsearch 顯示 2.8.6 為最新是過時快取,權威 `maven-metadata.xml` 才有 3.0.x。fallback(未用):若無相容 GA,改前端依 endpoint 手寫 `openapi.yaml`。
- **[P7] BigDecimal 在 JSON=字串,且 spec 同步**:`JacksonConfig` 全域把 BigDecimal 序列化成字串(會計不碰 float;前端型別為 string)。**關鍵**:springdoc/swagger-core 從 Java 型別推導,不知道自訂 serializer,故必須另用 `SpringDocUtils.replaceWithClass(BigDecimal,String)` 讓 spec 也標 string,否則 spec(number)會與 runtime(string)不一致、前端型別說謊。`OpenApiSpecIT` 斷言 spec 無 `type:number` 守此不變量。
- **[P7] `/api/auth/me` 放 iam.web**:`iam` 不在任何 ArchUnit 跨模組規則來源清單,新增 controller 安全、不需改 `ArchitectureTest`。此端點兼當「登入探針」(200=帳密對)與「角色來源」,維持 `anyRequest().authenticated()`(未認證 401)。自訂 `AuthenticationEntryPoint` 移除 `WWW-Authenticate: Basic` 以免瀏覽器原生彈窗蓋掉 SPA 登入頁。
- **[P7] 前端=repo 內 `frontend/` 獨立 Vite 專案**,不掛 Maven build;資料層用 `openapi-typescript`(commit `schema.d.ts`)+ `openapi-fetch` 單一 typed client(middleware 注入 Basic、攔 401/403)+ TanStack Query。`schema.d.ts` 與 `openapi/openapi.json` 進版控,前端 build 不需後端在線。
- **[P7] 環境=沙箱/VM 擋 Tomcat loopback**:本機無法跑真實 web server(`spring-boot:run` 起 Tomcat 報 `SocketException: Invalid argument: connect`)或 Vite dev server;OpenAPI spec 改以 MockMvc(`OpenApiSpecIT`)匯出,前端以 `vite build` 型別檢查驗證;實機點擊驗證留待 Stage 8 Docker demo(使用者機器 loopback 正常)。
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
