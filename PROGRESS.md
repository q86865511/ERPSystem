# PROGRESS — 製造業 ERP(作品集專案)

## 目前狀態
**Phase 1(商品與庫存)完成** — 分 3 段 PR 交付(Stage 1 ledger.api port、Stage 2 masterdata、Stage 3 inventory)。`inventory` 模組以**移動加權平均**估值、append-only 子帳、`StockAdjustment` 雙腿過帳在同一交易內同步呼叫 `ledger.api.LedgerPosting`。**Phase 1 驗收達成:庫存帳值 == GL Inventory 控制科目餘額(`InventoryReconciliationIT`)**;並行過帳無死鎖、移動平均精確。`mvn verify` 全綠(IT 19)。下一棒:Phase 2 採購到付款。路線圖見 `~/.claude/plans/pm-erp-enchanted-aurora.md`。

## 已完成
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
- (待指示)Phase 1 完成;下一棒 Phase 2 採購到付款(供應商、PO→GR→VendorBill→Payment、GR-IR 清算、AP 帳齡)。

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
