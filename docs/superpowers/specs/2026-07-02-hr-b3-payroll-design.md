# HR 模組 B3 — 薪資 → 總帳(設計)

日期:2026-07-02 · 分支:`feat/hr-b3` · 範圍:全端(後端 `com.erp.hr` + 前端 `features/hr`)· 使用者選定:**兩步(DRAFT→POST)** + **固定費率(稅 6% / 保 5%)**

## 動機

HR B1(主檔)+ B2(考勤/請假/工時)已上線。B3 收尾 HR:**跑當期薪資 → 過一張平衡分錄到 GL**,是 HR 唯一會過帳到總帳的一塊。透過既有 `ledger.api.LedgerPosting` port 過帳(鏡像 `SalesInvoiceService`),books 維持平衡、對帳 hero 維持綠。

## 會計分錄(每期一張,平衡)

- **Dr 6100** 薪資費用(gross 總額)
- **Cr 2200** 應付薪資 / 淨薪(net 總額)
- **Cr 2210** 代扣所得稅(tax 總額)
- **Cr 2220** 應付勞健保(insurance 總額)

平衡由建構保證:每人 `gross = net + tax + insurance`(net 吸收捨入)→ `sum(gross) = sum(net)+sum(tax)+sum(insurance)` → 借貸相等。**只進 GL、不碰 AP(2100)/AR(1200)/庫存子帳** → 對帳的子帳==GL 檢查不受影響、試算表仍平衡 → hero 綠。

## Flyway V19

- 新增科目:`6100` Salaries Expense(EXPENSE/DEBIT)、`2200` Net Pay Payable、`2210` Tax Withheld Payable、`2220` Insurance Payable(皆 LIABILITY/CREDIT)。(部門 `budget_account_code` 早已填 `6100`,此處補上該科目。)
- 新表 `payroll` + `payroll_line`。

## 領域模型(`com.erp.hr.domain`)

### Payroll(一期一張 + 過帳狀態機)
- 欄位:`id`、`periodYear`、`periodMonth`、`status`(`PayrollStatus`:DRAFT/POSTED)、`grossTotal`/`taxTotal`/`insuranceTotal`/`netTotal`、`postingDate`、`journalEntryId`(過帳後回填)。
- `UNIQUE(period_year, period_month)`(一期一張)。
- 狀態機:`run` 建立 → DRAFT;`markPosted(journalEntryId, postingDate)` DRAFT→POSTED(僅 DRAFT 可過,否則 `HrConflictException`→409)。

### PayrollLine(每員工一列)
- 欄位:`id`、`payroll_id`、`employeeId`、`gross`、`tax`、`insurance`、`net`。= 該員工的薪資單(payslip)明細。

## 計算(固定 demo 費率)

- 對每位 **ACTIVE** 員工:`gross = monthlySalary`(空則 0)、`tax = round(gross×0.06, 2)`、`insurance = round(gross×0.05, 2)`、`net = gross − tax − insurance`(net 吸收捨入,保證行平衡)。
- 費率常數(`TAX_RATE=0.06`、`INSURANCE_RATE=0.05`)清楚標為 demo 簡化;金額 scale 2。

## 應用層

- `PayrollService.run(year, month, actor)`:擋重複期(`HrConflictException`)、由 `EmployeeRepository` 取 ACTIVE 員工建 `PayrollLine`、算總額、存 DRAFT `Payroll`。
- `.post(payrollId, postingDate, actor)`:僅 DRAFT 可過;組平衡 `JournalEntryRequest`(4 腳,冪等鍵 `sourceDocType=PAYROLL` / `sourceDocId=payrollNo或id` / `sourceEvent=POST`)呼叫 `ledgerPosting.post`;回填 `journalEntryId`、轉 POSTED。postingDate 須落 OPEN 期間(由 ledger 自身守衛)。
- `.list()`、`.get(id)`(含 lines)。
- 例外沿用:重複期/非法轉態 → `HrConflictException`(409);未知 payroll → `HrNotFoundException`(404)。

## Web(`/api/hr/payroll`)

- `POST /api/hr/payroll`(body `{year, month}`)→ 201 DRAFT。
- `POST /api/hr/payroll/{id}/post`(body `{postingDate?}`,預設今天)→ POSTED。
- `GET /api/hr/payroll`(清單)、`GET /api/hr/payroll/{id}`(含 lines)。
- RBAC 免改:POST 在 `/api/hr/**` → ADMIN/HR;讀取限已登入(含 ACCOUNTANT 檢視)。response records 命名避開既有 schema。

## 前端(`features/hr`)

- HR 加 **Payroll** tab:跑當期(年/月)、列表(期間/狀態/gross/net 總額)、明細(per-employee lines = 薪資單)、**過帳 `StateButton`**(DRAFT→POST,依狀態守衛 + tooltip)、過帳後顯示分錄號(`journalEntryId`)。
- `modules/hr.ts` i18n(en/zh)+ `status.*` 已有 DRAFT/POSTED(POSTED 已存在)。nav HR children 加 `payroll`。`gen:api` 重生型別。RBAC 沿用 `hr.write`。

## Demo 資料(`DataSeeder`)

- 跑 + 過**上月**薪資(8 位在職員工),過帳後 books 仍平衡。若上月不在 2026 或期間問題,退回當月。

## 驗證

- `PayrollReconciliationIT`:run + post 後 **試算表仍平衡**、JE 四腳(6100/2200/2210/2220)金額正確、`sum(gross)=net+tax+insurance`;`reconciliation.healthy` 仍 true。
- `PayrollServiceIT`:由 ACTIVE 員工建 lines、重複期擋、post 守衛(僅 DRAFT)、冪等(重複 post 不重複過帳)。
- 領域單元 `PayrollTest`(狀態機)。
- `SeedDataIT` 加「有一張 POSTED 薪資」守衛。
- `./mvnw verify` 全綠(Testcontainers + ArchUnit + OpenApiSpecIT);前端 `npm run build` + `test:types` + Vitest 綠。

## 交付

- 分支 `feat/hr-b3`,mode (c) 自動 commit + 自動開 PR;**merge 保留給使用者**。push/PR 前更新 `PROGRESS.md`/`README*`(HR 加薪資過帳)。合併部署後 Oracle `down -v && up` 重 seed 才見 B3 資料。**🎉 完成後 HR 全模組(B1+B2+B3)落地。**
