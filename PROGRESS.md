# PROGRESS — 製造業 ERP(作品集專案)

## 目前狀態
**Phase 0(走骨架)完成**,`mvn verify` 全綠(15 個測試通過,含 6 個真 Postgres 整合測試)。複式記帳引擎核心(總帳、過帳服務、DB 不變量)可運作並有測試證實。架構與完整路線圖見計畫檔 `~/.claude/plans/pm-erp-enchanted-aurora.md`。下一步:Phase 1 商品與庫存。

## 已完成
- 2026-06-26 — 完成系統設計(多代理人設計工作流:6 維度設計 → 整合 → 對抗式審查),產出完整架構與實作計畫。
- 2026-06-26 — 定案技術棧:Java 21 + Spring Boot 4.1.0 + PostgreSQL 16 +(Flyway / Testcontainers / ArchUnit),前端 React 18 + TS + Mantine。
- 2026-06-26 — 以 start.spring.io 產生骨架(Maven Wrapper),依賴:web(mvc)、data-jpa、postgresql、validation、security、actuator、flyway、testcontainers、lombok、docker-compose。
- 2026-06-26 — compose / Testcontainers 釘 `postgres:16`;新增 GitHub Actions CI(`./mvnw -B -ntp verify`)。
- 2026-06-26 — **Phase 0 走骨架完成**:`shared.Money`;`ledger` 模組(Account / Journal / FiscalYear+Period / JournalEntry+Line / NumberSequence)+ repositories;Flyway V1(schema + 不變量:平衡 deferred constraint trigger、擋改/刪 POSTED 的 entry 與 line、CHECK 借貸非負且恰一邊、entry_no 唯一、idempotency 部分唯一索引)+ V2(GEN 日記帳、JE 序號、FY2026 月期間、科目表 TWD);`LedgerPostingService`(唯一過帳入口,含期間 OPEN 檢查、序號悲觀鎖、idempotency 前置檢查);`LedgerReportService` 試算表;Basic 認證(單一 ADMIN);手動分錄 + 試算表 REST;ArchUnit 邊界規則;ADR-0001(modular monolith)。
- 2026-06-26 — 測試:`mvn verify` 15 綠(MoneyTest 5、ArchitectureTest 3、ErpApplicationTests 1 走 Surefire;LedgerPostingServiceIT 6 走 Failsafe,真 Postgres)。

## 進行中
- 準備 Phase 1(商品與庫存):Item / Warehouse / Location 主檔、append-only StockLedgerEntry、移動加權平均(ItemCostState)、StockAdjustment 雙腿過帳,並建立 ledger 的 published `api`(供 inventory 跨模組過帳)。

## 待辦
- Phase 1 商品與庫存 → Phase 2 採購到付款 → Phase 3 訂單到收款 → Phase 4 製造(最低可展示里程碑)→ Phase 5 報表與期間結 → Phase 6 打磨與打包。詳見計畫檔。

## 已知問題
- 本機 `java`/`mvn` 不在沙箱 shell 的 PATH;建置需顯式設定 `JAVA_HOME=E:\JDK21` 並把 System32/PowerShell 路徑補進 PATH(否則 mvnw.cmd 找不到 powershell 無法 bootstrap)。
- Testcontainers 需 Docker daemon;若 `docker info` 連不上需先啟動 Docker Desktop(`C:\Program Files\Docker\Docker\Docker Desktop.exe`)。
- **教訓**:整合測試命名為 `*IT` 由 Failsafe 在 `verify` 跑,`mvn test` 不會跑到(Surefire 只跑 `*Test`/`*Tests`)。請用 `mvn verify` 跑完整測試;CI 已用 `verify`。
- README 的 CI badge 仍是 `OWNER/REPO` 佔位,待首次 push 建立 repo 後替換。

## 重要決策紀錄
- **建置策略=從零自建**:作品集價值在於展示自己的架構與 ERP 領域素養,而非 Odoo/Frappe 設定。
- **架構=模組化單體**:模組邊界=套件邊界,用 ArchUnit 在 CI 強制;跨模組過帳一律同一 `@Transactional` 內直接同步呼叫 `LedgerPostingService`,domain event 只給 audit/通知。
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
