# DataSeeder 擴充 — demo 資料豐富化(設計)

日期:2026-07-01 · 分支:`feat/dataseeder-enrichment` · 範圍:純後端(seed + IT)

## 動機

線上 demo 的 `seed` profile 目前只播 1 輪「買→做→賣」(2 品項、2 夥伴、1 張 PO/SO/WO)。四張 @mantine/charts 藍色儀表板雖然數字正確,但畫面稀疏:訂單漏斗只有 1 筆、庫存甜甜圈只有 2 段、AR/AP 帳齡全 0(顯示「—」)、生產進度看板空、HR 表在重部署後為空。

目標:把 seed 擴為**跨月、多品項、多夥伴、多輪**的資料集,讓每張儀表板自然變飽滿、AR/AP 帳齡跨桶、營收趨勢非平 —— 但**仍走真實過帳 service**(不造前端假數據)、維持 `DataSeeder` idempotent、`./mvnw verify` 全綠(對帳 hero 平衡)。規模採「較大版」。

## 已查證的關鍵前提

- **會計期間**:`V2__seed_reference_data.sql` 已播 FY2026 的 **1–12 月全部 OPEN**。今天 2026-07-01,故可安全回填 1–6 月分錄 → 自然形成 current / 1-30 / 31-60 / 61-90 / 90+ 各帳齡桶,無關期間問題。
- **對帳 `healthy`**(`ReconciliationService.reconcile`)= 試算表借貸相等 **且** 庫存/AP(2100)/AR(1200)子帳 == GL 控制科目。過渡科目(2150 GR-IR、1340 Deferred-COGS、1320 WIP)僅供顯示,但只要遵守下方鐵律即恆零。
- **既有測試**:`SeedDataIT` 硬斷言 RM-DEMO on-hand=50、FG-DEMO=20。故**保留既有 canonical cycle 原封不動**,新資料疊加於外。

## 鐵律(維持對帳全綠)

未完成的單據**只能停在過帳前狀態**;任何進到過渡科目的動作都必須成對走完:

| 動作 | 一旦做了就必須 | 否則 |
|---|---|---|
| `receive`(收貨) | `postBill`(開帳單) | GR-IR 2150 不歸零 |
| `issue`(領料) | `complete`(完工) | WIP 1320 不歸零 |
| `deliver`(出貨) | `postInvoice`(開發票) | Deferred-COGS 1340 不歸零 |

- 想讓「採購中/訂單漏斗/進度看板」有量 → 停在 `DRAFT` 或 `CONFIRMED`(SO)/ `RELEASED`(WO)——這些都**還沒過帳到過渡科目**,零會計風險。
- 想讓 **AR/AP 帳齡**有料 → 留「已開發票/帳單但**未收付款**」:invoice/bill 當下已把 GR-IR / Deferred-COGS 清掉,AR/AP 非零但子帳==GL、過渡科目仍零 → hero 綠。

## 設計:擴充內容(較大規模)

保留 RM-DEMO / FG-DEMO 既有單輪不動。新增:

### 主檔
- **品項 ~20**:RAW / WIP / FINISHED 混合;其中數個給 `reorderPoint` 但 on-hand 低/0 → 低庫存 alert + reorder report 有料;standardCost 分佈讓庫存甜甜圈多段。
- **夥伴 ~10**:數個廠商 + 數個客戶,讓 AP/AR 帳齡分佈在多夥伴。
- 倉:沿用 WH1 的 STOCK location(多倉非必要,低優先)。

### 採購 P2P(回填 1–6 月、多輪)
- **完整鏈**(receive→bill→pay):數輪跨月,灌庫存 + 讓移動平均成本有變化。
- **完整但未付款**(receive→bill,不 pay):散佈不同月 → **AP 帳齡多桶**。
- **只到 CONFIRMED / DRAFT**(不 receive):讓「採購中」有量。

### 銷售 O2C(回填多月)
- **完整鏈**(deliver→invoice→receive):認列營收/COGS,跨月 → 營收趨勢非平。
- **完整但未收款**(deliver→invoice,不 receive):散佈不同月 → **AR 帳齡多桶**。
- **只到 CONFIRMED**(不 deliver):訂單漏斗 confirmed 段有量。
- **只到 DRAFT**:漏斗 draft 段有量。

### 製造
- 數張工單跑完 **COMPLETED**(產出成品供銷售);數張停在 **RELEASED**(進度看板 / 待派工有料)。**絕不留 issue 未 complete**。
- BOM「單一有效 BOM」限制 → 每個 finished item 重用同一 bomId;工單消耗量 ≤ 已收貨在庫量。

### 時間分佈
posting date 跨 1–6 月,讓日後 C1 的營收/現金流趨勢、KPI 環比有非平時間序列可畫。

## 冪等

維持既有 `masterDataQuery.findPartnerByCode(VENDOR_CODE).isPresent()` 短路;全部新記錄用**固定 code**(重跑 `up` 安全,不重複播)。

## 驗證

- 強化 `SeedDataIT`:除既有 on-hand(50/20)+ `healthy`,加斷言 **AR 子帳 > 0、AP 子帳 > 0**(證明有留未收付款)且對帳仍 `healthy`(證明鐵律沒被打破)。
- `./mvnw verify`(Testcontainers 全 IT + ArchUnit + OpenApiSpecIT)全綠。seed 於 IT context 啟動時跑,順帶完整驗證 seed 程式 + 帳平 + 不回歸。
- 實作後以 Workflow 對 seeder 做**對抗式審查**:掃有無 receive-without-bill / issue-without-complete / deliver-without-invoice / 期間越界 / 破冪等 / 子帳風險。

## 交付

- 分支 `feat/dataseeder-enrichment`,純後端、低風險、可獨立 merge。
- push/PR 前更新 `PROGRESS.md`(README 純內部 seed 擴充、不影響對外用法 → 略過並說明)。
- Git 模式 (c):自動 commit + 自動開 PR;**merge 保留給使用者**(merge 觸發 Oracle 部署)。
- **線上 demo 重 seed**:因持久化 volume + idempotent seeder,線上需在 Oracle host `docker compose -f compose.demo.yaml down -v` 再 `up` 才會套用新資料(順帶解鎖 HR demo/截圖)。PR 說明附指令;由使用者在 Oracle 上執行。
