# HR 模組 B2 — 考勤 / 請假 / 工時(設計)

日期:2026-07-02 · 分支:`feat/hr-b2` · 範圍:全端(後端 `com.erp.hr` + 前端 `features/hr`)· 使用者選定:**三個獨立實體(最完整)**

## 動機

HR B1(#79/#80)已上線員工/部門/職位主檔。B2 加**時間管理**三塊:出勤、請假、工時。純 HR 資料,**不過帳到總帳**(薪資過帳留給 B3)。鏡像 B1 四層(`api/domain/application/web`)與既有狀態機風格(WorkOrder/SalesOrder)。

## 架構決策

- **模組邊界**:全部在既有 `com.erp.hr`;跨聚合參照以 **id** 持有(不用 JPA 關聯),沿用 B1 慣例。ArchUnit `hr_api_is_self_contained` / `hr_uses_only_published_ports` 維持綠。
- **RBAC 免改**:`SecurityConfig` 已有 `POST /api/hr/** → ADMIN/HR`、讀取限已登入;B2 端點都在 `/api/hr/` 下 → 自動繼承。核准/駁回/提交都是 POST → 同受 ADMIN/HR 保護。
- **無 GL 影響**:B2 不呼叫 `LedgerPosting`,不動對帳 hero。
- **無員工自助**:員工非 app 使用者 → 由 HR/admin 代為記錄出勤、代填/核准請假與工時。
- **Flyway**:最新 `V17` → 新增 `V18__hr_time.sql`(三表)。
- **服務切分**:不塞進 `HrService`(已管三個主檔);新增聚焦服務 `AttendanceService` / `LeaveService` / `TimesheetService`,各自單一職責。

## 領域模型(`com.erp.hr.domain` + enums 於 `com.erp.hr.api`)

### Attendance(每日出勤)
- 欄位:`id`、`employeeId`、`workDate`、`status`(`AttendanceStatus`:PRESENT/ABSENT/LATE/LEAVE/REMOTE)、`workedHours`(BigDecimal,可空)、`note`。
- 約束:`UNIQUE(employee_id, work_date)`(一員工一天一筆)。
- 行為:純記錄,無狀態機。

### LeaveRequest(請假 + 審批狀態機)
- 欄位:`id`、`employeeId`、`leaveType`(`LeaveType`:ANNUAL/SICK/PERSONAL/UNPAID)、`startDate`、`endDate`、`days`(BigDecimal)、`reason`、`status`(`LeaveStatus`:PENDING/APPROVED/REJECTED)、`decidedBy`、`decidedAt`。
- 狀態機:建立 → `PENDING`;`approve(actor)` / `reject(actor)` **僅能從 PENDING**,否則丟 `HrStateException`(→ 409)。
- 建構驗證:endDate ≥ startDate、days > 0。

### Timesheet(每週工時 + 提交狀態機)
- 欄位:`id`、`employeeId`、`weekEnding`(LocalDate,週結束日)、`regularHours`、`overtimeHours`(BigDecimal)、`status`(`TimesheetStatus`:DRAFT/SUBMITTED/APPROVED)、`note`。
- 約束:`UNIQUE(employee_id, week_ending)`。
- 狀態機:建立 → `DRAFT`;`submit()` DRAFT→SUBMITTED;`approve()` SUBMITTED→APPROVED;越級轉態丟 `HrStateException`。
- **與出勤區隔**:出勤=每日「在不在」;工時=每週「工作幾小時(含加班)」+ 審批,B3 薪資可據以算加班。

## 應用層

- `AttendanceService`:`record(employeeId, date, status, hours, note)`(擋重複日、擋未知員工)、`list(employeeId?, month?)`。
- `LeaveService`:`submit(...)`、`approve(id, actor)`、`reject(id, actor)`、`list(status?, employeeId?)`。
- `TimesheetService`:`create(employeeId, weekEnding, regular, overtime, note)`(擋重複週)、`submit(id)`、`approve(id)`、`list(status?, employeeId?)`。
- 例外:沿用 `HrNotFoundException`(未知員工/單據 → 404)、`DuplicateCodeException`(重複日/週 → 409);新增 `HrStateException`(非法轉態 → 409)。`HrExceptionHandler` 補對應。

## Web(全在 `/api/hr/` 下)

- `AttendanceController`:`POST /api/hr/attendance`、`GET /api/hr/attendance?employeeId=&month=`。
- `LeaveController`:`POST /api/hr/leave-requests`、`POST /api/hr/leave-requests/{id}/approve`、`.../reject`、`GET /api/hr/leave-requests?status=&employeeId=`。
- `TimesheetController`:`POST /api/hr/timesheets`、`POST /api/hr/timesheets/{id}/submit`、`.../approve`、`GET /api/hr/timesheets?status=&employeeId=`。
- 各自 request/response records;`OpenApiSpecIT` 維持綠(注意 response record 命名不撞既有 schema)。

## 前端(`features/hr`)

- `HRPage` 加三個 tab:`attendance` / `leave` / `timesheets`。
- 面板:`AttendancePanel`(清單 + 記錄 modal + 月份/員工篩選)、`LeavePanel`(清單 + 申請 modal + 核准/駁回 `StateButton`,依 status 守衛)、`TimesheetPanel`(清單 + 登錄 modal + 提交/核准 `StateButton`)。複用既有 `DataTable`/`DetailDrawer`/`StateButton`/`StatusBadge`。
- `HrDashboardPanel` 加 tile:**本月出勤率**、**待審請假數**(可選:待審工時數)。
- `i18n/messages/modules/hr.ts` 加 B2 字串(en/zh 成對);`AppLayout` NAV 的 HR children 加三子項;`auth/roles.ts` 的 `hr.write` 涵蓋新動作;`queryKeys` 加 keys;`gen:api` 重生型別(先 `OpenApiSpecIT` 匯出 spec → copy 到 `frontend/openapi/openapi.json` → `gen:api`,免起後端)。

## Demo 資料(`DataSeeder`,profile `seed`)

- 出勤:本月每位員工(8 人)工作日多為 PRESENT,少量 LATE/LEAVE/REMOTE + workedHours。
- 請假:數筆跨員工,狀態混 PENDING(供核准/駁回 demo)/APPROVED/REJECTED。
- 工時:數位員工近幾週,狀態混 DRAFT/SUBMITTED/APPROVED,含 regular + overtime。
- 全非 GL → 不影響對帳;維持 idempotent(在既有 VEND-DEMO 短路內)。

## 驗證

- 後端:`AttendanceIT`(記錄 + 清單 + 重複日 + 未知員工)、`LeaveRequestIT`(submit→PENDING、approve、reject、非法轉態、未知員工)、`TimesheetIT`(create→DRAFT、submit、approve、非法轉態、重複週)+ 請假/工時領域單元測試(狀態機)。`./mvnw verify` 全綠(Testcontainers + ArchUnit + OpenApiSpecIT)。
- 前端:`npm run build`(tsc+vite)+ `test:types` + Vitest 綠;新面板以 mock 截圖目視驗證(藍色版渲染)。
- 交付:分支 `feat/hr-b2`,mode (c) 自動 commit + 自動開 PR;**merge 保留給使用者**。push/PR 前更新 `PROGRESS.md` / `README*`(HR 模組說明加 B2)。合併部署後同樣需 Oracle `down -v && up` 重 seed 才見新資料。

## 交付順序(單一 PR 內)

1. 後端:enums → domain(含狀態機 + 單元測試)→ Flyway V18 → repositories → services → controllers/records/例外 → ITs → `./mvnw verify` 綠。
2. `gen:api`:匯出 spec → 生前端型別。
3. 前端:api hooks → 三面板 → dashboard tiles → i18n → nav → build/測試綠 → 截圖。
4. seed → 再 `./mvnw verify`(seed IT 綠)。
5. 文件(PROGRESS/README)→ commit → push → 開 PR。
