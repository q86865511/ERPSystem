# Phase 3 — PLANNED 後端 widget C1–C4(設計)

日期:2026-07-02 · 範圍:4 個獨立可 merge 的全端 PR(C1→C4)· 目標:把前端四處 PLANNED 佔位換成接真實後端的圖表。C5(OEE/設備)依使用者指示**暫緩**。

## 共同約束

- 每個 PR:後端(必要時 Flyway/domain,+ 唯讀彙總端點 + IT + `OpenApiSpecIT`)+ `gen:api` + 前端把 PLANNED 佔位換真圖 + 必要 seed + `./mvnw verify` 綠 + 前端 `build`/`test:types`/Vitest 綠。
- 讀側彙總優先用既有 `ledger.api.GeneralLedgerQuery`(`accountBalances(asOf)` / `linesForAccount(code, asOf)`)——多數 C1 端點**無需 migration**。
- DataSeeder 已有跨月發票/收付款 → 趨勢類端點天生有非平資料。
- merge 保留給使用者;合併後 Oracle `down -v && up` 重 seed 才見新資料。

## PR-C1 — 財會分析(`reporting`;含 budget-variance)

前端目標:`FinanceOverviewPanel` 的損益走勢 / 現金流 / 預算差異 + `DashboardPage` 銷售趨勢。

**讀側端點(無 migration,`ReportingService` 加方法 + `ReportingController` 加 GET):**
- `GET /api/reporting/revenue-trend?months=6` → 每月 `{month, revenue, cogs, grossMargin}`(由 `linesForAccount("4100"/"5100", asOf)` 依過帳月分桶,月淨額)。
- `GET /api/reporting/cash-flow?months=6` → 每月 `{month, inflow, outflow, net}`(由 1010 Cash 行:debit=流入、credit=流出)。
- `GET /api/reporting/kpi-summary` → 本月 vs 上月 `{revenue, grossProfit, cash}` 各 `{current, previous, deltaPct}`(環比)。

**budget-variance(需新增預算主檔):**
- 預算放 **`reporting`** 模組(reporting = 財務報表 + 預算規劃):`reporting.domain.Budget`(`period_year`, `account_code`, `monthly_budget`)+ `BudgetRepository` + `BudgetService`(建立/清單)。Flyway `V20__budget.sql`(表 + seed 幾個科目的月預算:6100 薪資、5100 COGS、4100 營收…)。
- `GET /api/reporting/budget-variance?year=&month=` → 每科目 `{accountCode, name, budget, actual, variance}`(actual = 該月 GL 淨額)。
- DataSeeder 播該年度數科目的月預算。

**前端**:`FinanceOverviewPanel` 三張 PLANNED → `@mantine/charts` LineChart(revenue-trend、cash-flow)+ 預算差異長條;`DashboardPage` 銷售趨勢 → LineChart(revenue-trend)。KPI 環比 delta 接 kpi-summary。

**IT**:`FinanceAnalyticsIT`(跑一小段 buy→sell + 一張預算,斷言 revenue-trend/cash-flow 月桶有值、budget-variance = budget − actual)。

## PR-C2 — 庫存 items-status treemap(`inventory`;無 migration)

前端目標:`InventoryDashboardPanel` 的熱度圖(heatmap)。

- `GET /api/inventory/items-status` → 每品項 `{sku, name, itemType, value, reorderPoint, status}`(status:OUT on-hand=0 / LOW ≤reorderPoint / OK),由 `InventoryReportService.onHandAll()` + masterdata reorderPoint,依 value 排序。放 `inventory.web`(新 `InventoryStatusController` 或併入既有)。
- **前端**:heatmap PLANNED → recharts `Treemap`(色依 OK/LOW/OUT)。新增共用 `components/charts/` treemap 包裝或直接用 recharts。
- **IT**:`ItemsStatusIT`(建品項 + 進貨,斷言 value/status 分類)。

## PR-C3 — 供應商準時率(`purchasing`;Flyway)

前端目標:`InventoryDashboardPanel` 的供應商績效卡。

- domain:`PoLine` 加 `expected_delivery_date`(Flyway `V21`,nullable,建單可帶);GR 的 `posting_date` = 實際到貨。
- `GET /api/purchasing/suppliers/performance` → 每供應商 `{partnerId, name, totalReceipts, onTime, onTimePct}`(on-time = GR 日期 ≤ 該行 expectedDeliveryDate)。
- **前端**:supplierPerf PLANNED → 卡片/清單(準時% 進度條)。
- DataSeeder:部分 PO 帶 expectedDeliveryDate(有些準時、有些遲)。**IT**:`SupplierPerformanceIT`。

## PR-C4 — 工單排程 → 真 Gantt(`manufacturing`;Flyway)

前端目標:`ManufacturingDashboardPanel` 的 Gantt。

- domain:`WorkOrder` 加 `planned_start` / `planned_end`(Flyway `V22`,nullable,create 可帶)。
- `GET /api/manufacturing/schedule` → 每工單 `{woNumber, itemId, plannedStart, plannedEnd, status, qtyToProduce, qtyProduced, pct}`(有排程日期者)。
- **前端**:gantt PLANNED → 自刻 `GanttBoard`(列=工單、bar=start→end + 進度 + 狀態色)。
- DataSeeder:工單帶 plannedStart/End(跨數週)。**IT**:`WorkOrderScheduleIT`。

## 交付順序 / Git

- 依序各自 PR:C1 → C2 → C3 → C4(每個 push/PR 前更新 `PROGRESS.md`/`README*`)。Flyway 序號:C1=V20、C3=V21、C4=V22(C2 無 migration)。
- mode (c) 自動 commit + 自動開 PR;**每個 merge 保留給使用者**。
- 每個 merge 後在 Oracle `down -v && up` 重 seed 使該 widget 上線。

## 驗證(每 PR)

- 後端 `./mvnw verify`(Testcontainers + ArchUnit「模組只 import 他模組 `*.api`」+ OpenApiSpecIT)綠;`gen:api` 型別對齊。
- 前端 `npm run build` + `test:types` + Vitest 綠。整合視覺待部署 + 重 seed 後點測。
