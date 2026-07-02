# ERP 系統測試報告(2026-07-02)

> 本報告為「測試與盤點」戰役的產出,**不含任何程式修改**。
> 供後續修復 session(Opus)直接執行:每個問題附重現步驟、證據、根因(file:line)、
> 修復方向與優先級,目標是零再調查即可動工。

## 0. 報告元資料

| 項目 | 值 |
|---|---|
| 受測版本 | main `4b3740fbdc1ac9757f24f4998bb476c6c407d788`(工作區乾淨) |
| 測試日期 | 2026-07-02(Asia/Taipei 下午) |
| 線上環境 | https://erp.terrychou.com(oracle 主機,部署即為此 commit,當日建置,bundle `index-BJq_9aAc.js`) |
| 本機環境 | `docker compose -f compose.demo.yaml`(localhost:8081,新版 seed 已確認:8 員工、3 OEE 設備) |
| 本機 JVM | `./mvnw -B -ntp verify`(Testcontainers PostgreSQL 16) |
| 測試帳號 | admin / accountant / warehouse / sales / hr / guest(密碼=帳號) |
| 工具 | Playwright 1.61(chromium)、axe-core 4.10.2、openapi-typescript 7.13、curl、node 24 |
| 測試分工 | 線上=唯讀(GET、瀏覽器點擊、預期 401/403 探針);**所有預期成功/400 的寫入一律打本機** |

## 1. 執行摘要

### 1.1 問題總表

| 編號 | 標題 | 嚴重度 | 領域 | 狀態 |
|---|---|---|---|---|
| ERP-001 | 側欄父選單點擊完全無反應(桌面/行動/鍵盤) | **P0** | 前端導覽 | 已確認 |
| ERP-013 | 全後端零 bean validation:空 body 令 10/12 寫入端點回 500 | **P1** | 後端契約 | 已確認 |
| ERP-014 | 狀態機違規回 500 而非 409/422(IllegalStateException 無人接) | **P1** | 後端契約 | 已確認 |
| ERP-006 | E2E 只有 smoke test,無導覽點擊測試(ERP-001 逃逸 CI 主因) | **P1** | 測試流程 | 已確認 |
| ERP-012 | 403 回應完全無 body → 前端只能顯示通用「請求失敗」 | P2 | 契約/UX | 已確認 |
| ERP-008 | 401 回應非 RFC 9457,且 charset=ISO-8859-1 | P2 | 後端契約 | 已確認 |
| ERP-015 | 狀態徽章/趨勢文字未達 WCAG AA(4.32 / 3.28 / 2.57 : 1) | P2 | UI a11y | 已確認 |
| ERP-004 | 除 Audit 外所有清單無分頁/排序(出勤頁一次 render 144 列) | P2 | 前端 UX | 已確認 |
| ERP-016 | 缺安全 header:無 HSTS / X-Content-Type-Options / CSP / X-Frame-Options | P2 | 部署加固 | 已確認 |
| ERP-010 | demo 財務數據真實感:單月薪資 532k 壓垮半年營收 133.7k;KPI 口徑月初必 -90% | P3 | Demo 資料 | 已確認 |
| ERP-005 | 頁面載入僅全域 spinner,無 skeleton(圖表跳入) | P3 | 前端 UX | 已確認(降級) |
| ERP-003 | 圖表主色 #2563EB 於暗色僅 3.0:1 壓線(原設計有意識,微調即可) | P3 | UI 視覺 | 已確認(降級) |
| ERP-007 | index.html 無 Cache-Control(僅靠 heuristic + Last-Modified) | P3 | 部署 | 已確認(降級) |
| ERP-009 | theme-color meta 與 favicon.svg 仍為舊 terracotta 色系(#C0532E) | P3 | 品牌一致性 | 已確認 |
| ERP-011 | 登入頁 demo 快速鍵缺 `hr` 帳號 | P3 | 前端 UX | 已確認 |
| ERP-017 | 首訪 onboarding overlay 攔截登入頁點擊(需先互動導覽) | P3 | UX 觀察 | 已確認 |
| ~~ERP-002~~ | ~~HR 員工姓名 null~~ | — | — | **排除(誤報)** |

### 1.2 健康證明(測過且乾淨的部分,Opus 免查)

- **前後端契約零漂移**:committed `frontend/openapi/openapi.json` = 線上 `/v3/api-docs`(103=103 operations,唯一差異 servers URL);`schema.d.ts` 以線上 spec 重生後逐字元一致(忽略 CRLF)。
- **RBAC 78/78 全吻合**:6 帳號 × 13 端點含 matcher 順序 nuance,完全符合 `SecurityConfig.java:79-98`;guest 全 POST 403、`GET /api/audit` 僅 admin。
- **O2C 寫入流程全綠**(本機):SO 建立→確認→出貨→發票→**自動轉 CLOSED**(PR #82 驗證);VAT 5% 計算正確;**寫入後對帳 healthy=true**(試算平衡、子帳=GL、過渡科目歸零)。
- **後端測試套件**:81 unit + 131 IT = 212 全過;jacoco 指令 72% / 分支 60%。
- **六帳號 GET 掃描**:38 端點 × 6 帳號全部如預期(非 admin 讀 audit 403,其餘 200)。
- **guest 前端 gating 佳**:建立按鈕正確隱藏;總帳手動分錄顯示「需要 ACCOUNTANT 角色」明確提示(截圖 [guest-ledgerManualEntry.png](test-evidence/2026-07-02/guest-ledgerManualEntry.png))。
- **i18n 乾淨**:en 模式無中文洩漏(唯一 CJK 是語言切換鈕「中」,by design)、無 raw key 露出。
- **行動版無水平溢出**(375px:docScrollW=375,表格內部捲動)。
- **actuator 未對外曝露**:公網 `/actuator/health`、`/actuator/prometheus` 均回 SPA fallback(nginx 不代理 /actuator)。
- **部署為最新版且健康**:容器全 healthy、log 無錯誤、Flyway V23 到位、磁碟 14% / 記憶體 17%。

### 1.3 整體結論(三句)

系統的**會計核心、API 契約與權限模型非常紮實**(對帳全平、契約零漂移、RBAC 全吻合、212 測試全綠)。
最大的問題集中在**表層互動與錯誤處理**:一個讓 8/10 模組「點不進去」的 P0 導覽 bug,以及「驗證缺失+例外未映射」造成的 500 群(P1)。
UI 視覺基礎好(亮/暗雙主題、藍色企業風),依 §6 設計提案做工具列/分頁/語意色/對比修正後可達作品集水準。

## 2. 測試範圍與方法

- **方法紀律**(systematic-debugging):每個問題 = 症狀→重現→證據→根因(file:line)→修復方向;先重現再定性,不憑推測;「已確認」都對應實際 artifact。
- **線上/本機分工**:見 §0 表。線上唯一的「寫入類」請求是 guest POST(必在 security filter 被擋、不觸 controller)。
- **拋棄式腳本**:見附錄 B(6 支,置於 session scratchpad,不入庫,可依說明重建)。
- **未涵蓋範圍**(明列,避免誤以為已測):
  - 併發寫入(gapless 序號競態)、效能/壓測、長期資料量下的前端表現
  - 期間關閉/年結流程的線上實操(已有 IT 覆蓋)
  - 瀏覽器相容性(僅 Chromium)、真實行動裝置(僅 viewport 模擬)
  - 付款分配 edge cases(超額/部分/多單分配)— 建議列入 Opus 批次二的回歸測試
  - Cloudflare/Caddy 層的設定審查(僅由外部觀測 header)

## 3. 問題詳情

### 3.1 ERP-001 側欄父選單點擊完全無反應(P0|已確認)

- **症狀**:登入後點擊側欄「主檔/採購/銷售/製造/庫存/報表/總帳/人力資源」(10 個主選單中 8 個)毫無反應 — 不導航、子選單不展開、無任何錯誤訊息。鍵盤 Enter/Space 同樣無效。行動版點擊後抽屜關閉但停留原頁(「點了、選單收起來、什麼都沒發生」)。
- **重現步驟**:
  1. 開 https://erp.terrychou.com,以任一帳號登入(落在儀表板)
  2. 點側欄「採購」→ URL 仍為 `/`,子選單未展開
  3. 鍵盤:Tab 聚焦「採購」→ Enter 或 Space → 同樣無反應
  4. 對照:點「儀表板」「審計軌跡」(無子項)→ 正常導航
- **證據**:
  - 10 選單點擊矩陣 [nav-matrix.json](test-evidence/2026-07-02/nav-matrix.json):8 個父項 `navigated:false, submenuExpanded:false`;`/audit` 正常;Enter/Space 皆 false
  - 截圖:[桌面點擊無反應](test-evidence/2026-07-02/ERP-001-desktop-click-masterdata-noop.png)、[行動版點擊後](test-evidence/2026-07-02/ERP-001-mobile-after-click-sales.png)、[直接輸 URL 可用](test-evidence/2026-07-02/ERP-001-direct-url-works.png)
  - console 零錯誤(無聲失敗);模組頁內子選單可點(`/masterdata` → `?tab=partners` ✓)→ 證明「只有從外部進入模組」這條路斷了
- **根因**(原始碼層面確認):
  - `frontend/src/components/AppLayout.tsx:283-307`:父項 = Mantine `NavLink` + `component={Link}` + **受控 `opened={active}`**(active 由 pathname 推導)
  - Mantine 9.4.1 `NavLink.mjs:54-60`:`withChildren` 時點擊 → `event.preventDefault()` + toggle 內部 opened
  - 因果鏈:preventDefault → React Router `<Link>` 見 `defaultPrevented` 放棄導航 → pathname 不變 → `active`/受控 `opened` 不變 → 子選單也不展開 → **完全 no-op**。toggle 對受控 prop 亦無效
  - 為何 CI 沒抓到:E2E 僅 `e2e/smoke.spec.ts`(登入頁 title);截圖腳本用 `page.goto` 繞過選單(→ ERP-006)
- **修復方向**(擇一,建議 A):
  - **A**:父項移除 `component={Link}`,改 `onClick={() => navigate(item.to)}` 手動導航(保留受控 `opened={active}`,accordion-follows-route 邏輯不變)。Mantine 的 preventDefault 對非 anchor 無害,導航由我們自己做
  - **B**(配合 §6 設計提案):父項拆兩個互動區 — 文字列導航、右側 chevron 只展開/收合(見 [mockups/nav-sidebar.html](test-evidence/2026-07-02/mockups/nav-sidebar.html))
- **影響檔案**:`frontend/src/components/AppLayout.tsx`
- **建議回歸測試**:Playwright — 登入→逐一點擊 10 個側欄項→斷言 URL 變更與子選單展開;含鍵盤 Enter 與行動版(§10-1)

### 3.2 ERP-013 全後端零 bean validation(P1|已確認)

- **症狀**:對 12 個代表性 POST 端點送 `{}`(合法 JSON、缺所有欄位),10 個回 **500 Internal Server Error**(Spring 預設 error JSON),僅 `POST /api/ledger/journal-entries` 與 `POST /api/inventory/adjustments` 回 400(手動 null 檢查)。
- **重現**(本機):`curl -X POST localhost:8081/api/masterdata/items -H "authorization: Bearer <admin>" -H 'content-type: application/json' -d '{}'` → 500
- **證據**:[rbac-results.json](test-evidence/2026-07-02/rbac-results.json) `fiveHundreds`(19 筆 500);500 body 無 stack trace 洩漏 ✓;正確對照組 400 body:`{"detail":"postingDate is required",...}` (problem+json)
- **根因**:`grep -r "@Valid\|jakarta.validation" src/main/java` = **0 命中**。所有 controller 都是裸 `@RequestBody`(如 `MasterDataController.java:46-102`),`{}` 反序列化成全 null record 直達 service 炸 NPE。OpenAPI spec 一致地 `required: []` → typed 前端在編譯期也擋不住漏欄位。
- **修復方向**:request record 加 `@NotNull/@NotBlank/@Valid`(springdoc 會自動把 required 寫進 spec)→ controller 參數加 `@Valid` → 全域 `@ControllerAdvice` 把 `MethodArgumentNotValidException` 映成 400 problem+json(附欄位錯誤 extension)。
- **影響檔案**:各 `*/web/*Controller.java` 的 request records、新增全域 advice(建議 `com.erp.config` 或 `com.erp.web`)
- **回歸測試**:參數化 IT — 每個 POST 端點 × `{}` 斷言 400 + problem+json(§10-3)

### 3.3 ERP-014 狀態機違規回 500 而非 409/422(P1|已確認)

- **症狀**:對已 CONFIRMED 的銷售單再打一次 `/confirm` → **500**(預期 409 或 422 + 訊息)。
- **重現**(本機):建 SO → confirm → 再 confirm → 500(transcript:附錄 B flow-smoke)
- **根因**:domain 守衛全丟裸 `IllegalStateException`(`SalesOrder.java:76,85,88,102,109`、`SalesInvoice.java:118,136,142,154,157`,其他模組同型);8 個模組的 `@RestControllerAdvice` 只接自家自訂例外(NotFound/Validation/Duplicate 類),**無人接 IllegalStateException** → Spring fallback 500。
- **修復方向**:全域 advice 把 `IllegalStateException` → 409 problem+json(detail 用例外訊息,內容本來就寫得很好:「only a DRAFT order can be confirmed, was CONFIRMED」);`IllegalArgumentException` → 400。或把 domain 改丟自訂例外 — 工程量大,不建議首選。
- **與 ERP-008/012/013 合併為「錯誤契約統一」工作包**:目前同一系統有 4 種錯誤格式 — problem+json(domain)/手寫 401 JSON/Spring 預設 500/空 403。

### 3.4 ERP-006 E2E 覆蓋不足(P1 流程|已確認)

- **現況**:`frontend/e2e/smoke.spec.ts` 只驗登入頁 title;無任何點擊/導覽/表單測試 → P0 的 ERP-001 全綠通過 CI。
- **修復方向**:§10 的回歸測試清單納入 CI(`frontend` job 加 `npx playwright test`,PLAYWRIGHT_BASE_URL 指向 CI 內起的 compose 或 preview build)。

### 3.5 ERP-012 403 回應完全無 body(P2|已確認)

- **症狀**:guest(無角色)POST 任何寫入端點 → `403`,**content-type: null、body 空字串**。
- **證據**:sweep probes `guest_post_403`(線上實測);前端對照:`client.ts:70-72` 的 `onForbidden` 是刻意 no-op(留給呼叫端),呼叫端 `notify.ts:5-12 errorMessage()` 依 `detail ?? title ?? message` 取值 → 空 body 永遠 fallback 成通用「請求失敗」— 使用者無從得知是權限問題。
- **根因**:`SecurityConfig` 未設定 `accessDeniedHandler`(Spring 預設 403 空回應)。
- **修復方向**:後端加 `accessDeniedHandler` 輸出 problem+json(`title: "Forbidden", detail: "此操作需要 <角色> 權限"`);前端可另對 403 映 i18n 權限訊息作為雙保險。
- **緩解因素**:前端 gating 做得好(§1.2),demo 中一般使用者很難踩到;但對 API 直接使用者(Swagger 演示)體驗差。

### 3.6 ERP-008 401 回應非 RFC 9457(P2|已確認)

- **實測**:匿名 GET → `401`、body `{"status":401,"error":"Unauthorized"}`、`content-type: application/json;charset=ISO-8859-1`。
- **根因**:`SecurityConfig.java:111-116` 手寫 entry point(為了抑制 WWW-Authenticate,動機正確)。
- **修復方向**:同一 entry point 改寫 problem+json + UTF-8(`{"type":"about:blank","title":"Unauthorized","status":401}`),保持不加 WWW-Authenticate 的行為;對照組 404 已是標準 `application/problem+json` 可做範本。

### 3.7 ERP-015 狀態徽章/趨勢文字對比不足(P2|已確認)

- **實測**(axe-core,亮色 Dashboard,serious ×12):
  - 「實際資料」「試算表已平衡」teal 淺色 Badge:**#087F5B on #C3FAE8 = 4.32:1**(10px bold,需 4.5)× ~10 處
  - KPI 趨勢文字橘 **#FD7E14 on #FFF = 2.57:1**;紅 **#FA5252 on #FFF = 3.28:1**
  - login 頁與 masterdata 表格頁 axe = 0 violations ✓
- **修復方向**:語意文字色一律用 Mantine `.9` 色階(red.9 `#C92A2A` ≈7:1、orange.9、teal.9);Badge 用 `autoContrast` 或加大字級/改 filled variant。與 §6.4 design token「語意色雙層(bg 用 .0-.1/文字用 .9)」一致。
- **影響檔案**:`DashboardPage` KPI/趨勢文字、共用 Badge 用法(搜 `variant="light"` Badge)。

### 3.8 ERP-004 清單無分頁/排序(P2|已確認)

- **實測**:items 15 列、銷售單 24 列、**出勤 144 列**一次 render;`hasPagination:false, hasSortIcons:false`(Audit 頁除外)。資料量成長後(尤其出勤/審計類)會顯著劣化。
- **修復方向**:共用 `DataTable` 加 client-side 分頁(預設 25/頁)+ 欄位排序;出勤/audit 類後端已有 `month`/pagination 參數可接。見 [mockups/table-page.html](test-evidence/2026-07-02/mockups/table-page.html) 與 §6 對比圖。

### 3.9 ERP-016 缺安全 header(P2|已確認)

- **實測**(`curl -sI https://erp.terrychou.com/`):無 `Strict-Transport-Security`、`X-Content-Type-Options`、`Content-Security-Policy`、`X-Frame-Options`/`frame-ancestors`。
- **修復方向**(nginx.conf 或 Caddy 層擇一,建議 nginx 統一管理):`add_header Strict-Transport-Security "max-age=31536000" always;`、`X-Content-Type-Options nosniff`、`X-Frame-Options DENY`(或 CSP `frame-ancestors 'none'`)、基本 CSP(SPA 同源 + data: 圖片)。逐項驗證不破壞 Swagger UI。

### 3.10 ERP-010 demo 財務數據真實感(P3|已確認)

- **a) 損益比例**:income-statement — 營收 133,700(6 個月累計)vs 薪資 532,000(單月,8 人 68k–98k 合計吻合)→ 淨利 **-483,472**。數學正確、是 seeder 比例失衡:demo 首頁像慘賠公司,作品集觀感差。
- **b) KPI 口徑**:kpi-summary 用「本月(僅 2 天)vs 上月(完整)」→ revenue -91.7%、netCash -100.9% 恆為慘烈。
- **修復方向**:a) DataSeeder 把銷售單價/量放大一個數量級(或薪資縮小),讓月營收 > 月薪資;b) KPI 比較基準改「上月同期(MTD)」或在 UI 標注口徑。
- **影響檔案**:`src/main/java/com/erp/bootstrap/DataSeeder.java`、`reporting` KPI 查詢。

### 3.11 ERP-005 載入狀態(P3|已確認,自 P2 降級)

- **實測**(API 延遲 1.5s、0.7s 截圖):有全域 spinner(1 個 Loader)、無 skeleton;KPI/圖表卡整片跳入。[截圖](test-evidence/2026-07-02/probe-dashboard-loading-700ms.png)
- **修復方向**:Dashboard 卡片與 DataTable 加 Mantine `Skeleton`(高度同實件),消除版面跳動。

### 3.12 ERP-003 圖表主色暗色壓線(P3|已確認,自 P2 降級並更正)

- **更正**:`palette.ts:2-5` 有明確設計註解(mid-tones readable on both surfaces)— 全 8 色實算對暗底(#242424)對比 3.7–6+:1 大多過關,**僅主色 #2563EB = 3.0:1 壓線**(WCAG 圖形 3:1 門檻);暗色截圖目視可讀([dark-dashboard.png](test-evidence/2026-07-02/dark-dashboard.png))。
- **修復方向**(微調):圖表色改讀 CSS 變數,暗色下主系列換 `#3B82F6` 或 `#60A5FA`(與 theme.ts 的 dark brand `#3B7EE8` 一致),其餘色不動。

### 3.13 ERP-007 index.html 快取政策(P3|已確認,降級)

- **實測**:`GET /` 無 Cache-Control(僅 Last-Modified);`Cf-Cache-Status: DYNAMIC`(Cloudflare 不快取 HTML ✓);assets 為 immutable 1y ✓。風險=瀏覽器 heuristic 快取讓舊 index.html 在部署後短暫引用已清除的舊 hash assets。
- **修復方向**:nginx `location = /index.html { add_header Cache-Control "no-cache"; }`(try_files 對 `/` 同樣適用)。

### 3.14 ERP-009 舊 terracotta 品牌殘留(P3|已確認)

- **實測**:`index.html` `<meta name="theme-color" content="#c0532e">`;`favicon.svg` 用色 #2A1F1A/#4A3A30/**#C0532E**/#E8937D(warm terracotta 全家桶)— 主題已改藍 `#2563EB`(PR #76)但這兩處沒跟上;Android/行動瀏覽器網址列與分頁圖示仍是橘紅。
- **影響檔案**:`frontend/index.html`、`frontend/public/favicon.svg`。

### 3.15 ERP-011 登入頁快速鍵缺 hr(P3|已確認)

- `LoginPage.tsx:21` `ROLE_USERS = ['admin','accountant','warehouse','sales']` — UserSeeder 有 `hr` 帳號,登入頁沒露出,demo HR 寫入功能無快速入口。

### 3.16 ERP-017 首訪 onboarding overlay 攔截登入(P3|已確認)

- **實測**:首訪(無 `erp.onboarding` localStorage)登入頁被 overlay 蓋住,demo 帳號按鈕點不到,需先與導覽互動;`elementFromPoint` 證實按鈕區頂層是 overlay div([截圖](test-evidence/2026-07-02/misc-login-first-visit-overlay.png))。自動化需寫入 `{completed:true, currentStep:0}` 兩欄位(缺一驗證失敗,`onboardingPreference.ts:18-23`)。
- **修復方向**(擇一):tour 移到登入後首頁才開始;或 overlay 對 callout 以外區域放行點擊;或第一步就聚焦 demo 帳號並提供顯眼「跳過」。

### 3.17 ~~ERP-002~~ HR 員工姓名 null — 排除(誤報)

- 先前部署檢查回報「some null names」;本戰役實測 8 名員工 firstName/lastName 齊全,唯一 null 欄位是 `terminationDate` ×8(在職者語意正確)。**判定為把 `terminationDate: null` 誤讀成姓名缺失**。無需修復。

## 4. RBAC 權限矩陣測試結果

規則來源:`SecurityConfig.java:79-98`(POST-only gating + GET /api/audit)。
**結果:78/78 格全部吻合預期,零違規**(細節 [rbac-results.json](test-evidence/2026-07-02/rbac-results.json))。

| 端點(POST 除註明) | admin | accountant | warehouse | sales | hr | guest |
|---|---|---|---|---|---|---|
| masterdata/items | ✓允 | 403 | 403 | 403 | 403 | 403 |
| hr/departments | ✓允 | 403 | 403 | 403 | ✓允 | 403 |
| purchasing/vendor-bills | ✓允 | ✓允 | 403 | 403 | 403 | 403 |
| sales/sales-invoices、customer-returns | ✓允 | ✓允 | 403 | 403 | 403 | 403 |
| payments/out、ledger/journal-entries | ✓允 | ✓允 | 403 | 403 | 403 | 403 |
| sales/sales-orders | ✓允 | 403 | 403 | ✓允 | 403 | 403 |
| sales/deliveries、purchasing/POs、inventory/adjustments、manufacturing/WOs | ✓允 | 403 | ✓允 | 403 | 403 | 403 |
| GET audit | 200 | 403 | 403 | 403 | 403 | 403 |

(✓允 = 通過授權層,以 400/422/500 進到驗證/服務層 — 500 部分即 ERP-013/014)

**風險備註(給 Opus)**:全系統無 PUT/PATCH/DELETE mapping,所以「只 gate POST」目前是完備的;但這是**慣例而非防呆** — 未來新增第一個 PUT 端點會落入 `anyRequest().authenticated()`,guest 也能寫。建議:security 規則改按 `/api/**` 非 GET 全部要求角色,或加 ArchUnit 規則禁止未涵蓋的寫入方法。

## 5. API 契約與錯誤格式

### 5.1 規格漂移 — 零漂移 ✓(詳 §1.2)

附註:Windows 開發機上 `gen:api:check` 的 diff 會受 CRLF 影響(本次驗證需 `--strip-trailing-cr` 才判等)— git autocrlf 下無實害,留意即可。

### 5.2 錯誤回應形狀實測(線上)

| 情境 | status | content-type | body | 判定 |
|---|---|---|---|---|
| 匿名 GET | 401 | application/json;**charset=ISO-8859-1** | `{"status":401,"error":"Unauthorized"}` | ERP-008 |
| guest POST | 403 | **null** | **空** | ERP-012 |
| 不存在 id | 404 | application/problem+json | `{"detail":"no item with id 999999",...}` | ✓ 標準 |
| 空 body POST(本機) | **500** ×10/12 | application/json | Spring 預設(無 stack trace ✓) | ERP-013 |
| 重複 confirm(本機) | **500** | application/json | Spring 預設 | ERP-014 |
| journal-entries `{}`(本機) | 400 | application/problem+json | `postingDate is required` | ✓ 應為全站範本 |

### 5.3 前端錯誤處理對照

`notify.ts errorMessage()` 期待 `detail/title/message` → 與 problem+json 相容 ✓;401 由 client 自動 refresh/導回登入 ✓;403/500 的非標準形狀 → 通用「請求失敗」(資訊量不足,隨 ERP-012/013/014 修復自然解決)。

## 6. UI/UX 設計審查與完整設計提案

### 6.1 逐頁審查摘要(10 路由 × 亮/暗/行動,證據見 test-evidence/)

| 面向 | 現況評價 | 主要建議 |
|---|---|---|
| 視覺基調 | 藍色企業風、亮/暗雙主題完整、卡片系統一致 — **基礎好** | 維持;按下列點微調 |
| 資訊層級 | Dashboard 卡片排布合理;「實際資料」徽章每卡重複 ×9 成噪音 | 徽章整頁一枚移頁首(對比圖 ②③) |
| KPI 表達 | 淨利 -483k 黑字平鋪;API 已有 deltaPct 但未用 | KPI 卡加 ▲▼ delta chip、負值紅字(red.9) |
| 圖表 | 漏斗無階段標籤(只有數字);趨勢圖 OK | 漏斗改水平階段條+標籤;色盤入 CSS 變數 |
| 表格 | 藍斑馬+藍表頭+藍 hover 三層互搶;無工具列/排序/分頁 | 見 table mockup:白底細線+工具列+分頁 |
| 回饋 | 有全域 spinner、錯誤 toast;無 skeleton | 卡片/表格 skeleton(ERP-005) |
| 響應式 | 375px 無溢出、表格內捲 ✓;抽屜行為正確 | 修 ERP-001 後行動導覽即完整 |
| a11y | login/表格頁 axe 0 ✓;Dashboard 12 serious(ERP-015);焦點環存在 | 語意色 .9 化;跳至主內容連結 |
| i18n | zh/en 完整、無洩漏 ✓ | — |
| 空狀態 | EmptyState 元件存在 | 清單空狀態加 CTA(「新增第一筆…」) |

### 6.2 設計對比圖(現況 vs 提案)

1. **導覽側欄**:[design-before-after-nav.png](test-evidence/2026-07-02/design-before-after-nav.png) — 父項「文字=導航、chevron=展開」雙 hit-area、active rail、鍵盤規格(修復 ERP-001 的互動規格)
2. **Dashboard**:[design-before-after-dashboard.png](test-evidence/2026-07-02/design-before-after-dashboard.png) — KPI delta、負值語意色、徽章去重、漏斗標籤、提醒卡行動連結
3. **清單頁**:[design-before-after-table.png](test-evidence/2026-07-02/design-before-after-table.png) — 工具列(搜尋/篩選/筆數/匯出)、排序、分頁、金額欄右對齊、subtle 斑馬

可互動 mockup 原始檔:[mockups/](test-evidence/2026-07-02/mockups/)(自足 HTML,直接開瀏覽器看)。

### 6.3 Design Token 規劃(提案)

> 原則:全部落在 Mantine theme + CSS 變數,**不引入新依賴**;沿用現有 brand 藍。

- **語意色(雙層結構)**:每個語意色取「淺底 `.0/.1` + 深字 `.9`」成對使用,徹底解決 ERP-015
  - `positive`: teal.0 底 / teal.9 字(#E6FCF5 / #087F5B→加深至 ≥4.5 用 #066649)
  - `negative`: red.0 / red.9(#FFF5F5 / #C92A2A)
  - `warning`: yellow.0 / orange.9(#FFF9DB / #B54708 級)
  - `info`: brand.0 / brand.7
- **圖表色盤 CSS 變數化**:`palette.ts` 改輸出 `var(--erp-chart-1..8)`,`:root` 亮色=現值;`[data-mantine-color-scheme="dark"]` 下 `--erp-chart-1: #3B82F6`(其餘沿用)→ 解 ERP-003 且未來可整體換膚
- **數字排版**:金額/數量一律 `font-variant-numeric: tabular-nums` + 右對齊(theme 全域 `.erp-num` 或 Table 樣式)
- **間距節奏**:卡片內 padding `md`(16)、卡片間 gap `md`、區塊間 `xl`(32);表格 cell `9px 12px`(緊湊模式 `6px 10px` 供大表)
- **元件慣例**:PageHeader(標題+說明+右側動作)、Toolbar(搜尋+篩選+筆數+匯出+主動作)、StatusBadge(1px 邊框+語意雙層色)、EmptyState(插圖+CTA)、Skeleton 高度=實件
- **品牌一致性**:theme-color meta 與 favicon 改藍(ERP-009);dark 下 theme-color `#1A1B1E`

### 6.4 改善建議清單(依優先級)

| # | 建議 | 對應 | 優先 |
|---|---|---|---|
| 1 | 修復導覽互動並採 nav mockup 規格 | ERP-001 | P0 |
| 2 | 語意色雙層化(badge/趨勢字) | ERP-015 | P2 |
| 3 | DataTable 工具列+排序+分頁 | ERP-004 | P2 |
| 4 | 表格去藍斑馬 → 白底細線+hover | 設計 | P2 |
| 5 | KPI 卡 delta chip + 負值紅字 + 口徑標注 | ERP-010b | P2 |
| 6 | 「實際資料」徽章整頁一枚 | 設計 | P3 |
| 7 | Skeleton 載入狀態 | ERP-005 | P3 |
| 8 | 漏斗階段標籤/水平化 | 設計 | P3 |
| 9 | 暗色主系列色變數化 | ERP-003 | P3 |
| 10 | favicon/theme-color 換藍 | ERP-009 | P3 |
| 11 | 登入頁補 hr 快速鍵 | ERP-011 | P3 |
| 12 | onboarding 首訪不擋登入 | ERP-017 | P3 |
| 13 | 空狀態 CTA、跳至主內容連結 | a11y | P3 |

## 7. 後端測試套件執行結果

- `./mvnw -B -ntp verify`:**BUILD SUCCESS,81 unit(surefire)+ 131 IT(failsafe)= 212,0 失敗**,總時 1:03
- jacoco(合併報告):**指令 72% / 分支 60%**(與 badge 一致)
- 本機 compose stack 對新 seed 驗證:8 員工、3 OEE 設備、Flyway 23 migrations

## 8. 架構性限制(設計取捨,非缺陷 — 附源碼佐證)

| 限制 | 佐證 | 備註 |
|---|---|---|
| 單層 BOM(無巢狀組裝樹) | manufacturing/domain | ADR 級取捨 |
| scrapPct 存而未用 | `BomComponent.java:21`:「reserved column for a future scrap allowance」 | 明文保留欄位 |
| 無使用者管理 API(帳號只靠 seeder) | `V15__iam_users_and_roles.sql` 設計註解 | demo 定位 |
| 無 rate limiting(app 與 nginx 層皆無) | SecurityConfig / nginx.conf | 公網 demo 可考慮 CF 層規則 |
| 無軟刪除/封存 | 各 entity | 刪除保護未測(未涵蓋範圍) |
| Swagger/v3 api-docs 公開 | `SecurityConfig.java:71-74` 註解「production 由 nginx 擋」,但 demo nginx 有代理(`nginx.conf:30-33`) | demo 屬刻意;註解與現況不一致,留意 |

## 9. 給 Opus 修復場次的路線圖(依相依性排序)

> 通則:每批次 = 修復 + 對應回歸測試 + 驗證命令;全程遵守專案 CLAUDE.md 的 git 流程(需先問 session 模式)。

**批次一 — P0 止血(獨立,最先)**
1. ERP-001 導覽修復(方案 A 或 mockup 規格 B)
2. 同 PR 加 Playwright 導覽點擊測試(§10-1)並掛進 CI(ERP-006 部分)
   驗證:`npx playwright test`(對 preview/compose)+ 手動點 10 選單

**批次二 — 錯誤契約統一(後端,一個 PR)**
3. ERP-013 bean validation(request records + @Valid + advice 400)
4. ERP-014 全域 advice:IllegalState→409、IllegalArgument→400
5. ERP-012 accessDeniedHandler 403 problem+json
6. ERP-008 401 problem+json + UTF-8
   驗證:§10-3 參數化錯誤格式 IT + 既有 212 測試不退步;`gen:api` 重生 spec(required 會變動)→ 前端型別同步

**批次三 — UI/UX(可拆多 PR)**
7. ERP-015 語意色雙層化(token 先行)
8. ERP-004 DataTable 工具列+排序+分頁(套 table mockup)
9. ERP-005 skeleton、§6.4 #4-8 設計項
   驗證:axe CI 檢查(§10-4)+ 截圖重拍

**批次四 — polish/hardening(小 PR 群)**
10. ERP-016 nginx headers、ERP-007 index.html no-cache(同檔一起)
11. ERP-009 favicon/theme-color、ERP-011 hr 快速鍵、ERP-017 onboarding
12. ERP-010 seeder 比例 + KPI 口徑(注意 oracle 需 `down -v` 重種才會生效)

## 10. 建議新增的回歸測試

1. **Playwright 導覽測試**:登入→逐一點 10 側欄項→斷言 pathname 變更+子選單展開;鍵盤 Enter;行動版 burger→點擊→斷言導航+抽屜關閉(直接以本報告 nav-probe 邏輯改寫成 spec)
2. **RBAC 參數化 IT**:6 帳號 × 規則表(§4)期望 allow/deny — 後端已有 RbacIT 可擴充成全矩陣
3. **錯誤格式參數化 IT**:每 POST 端點 × `{}` → 400 problem+json;非法轉移 → 409;401/403 形狀斷言
4. **axe CI 檢查**:login/dashboard/一個表格頁,serious 以上即 fail
5. **HR/seed 斷言**:SeedDataIT 加「月營收 > 月薪資」的真實感 guard(防 ERP-010 回歸)
6. **契約漂移**:CI 已有 `gen:api:check` 腳本,確認有掛進 frontend job(未掛則補)

## 附錄 A. 證據索引(docs/test-evidence/2026-07-02/)

| 檔案 | 內容 |
|---|---|
| ERP-001-desktop-click-masterdata-noop.png | 桌面點「主檔」後停在儀表板(URL 戳記) |
| ERP-001-mobile-drawer-open.png / ERP-001-mobile-after-click-sales.png | 行動版點擊前/後(抽屜關、未導航) |
| ERP-001-direct-url-works.png | 直接輸 URL 正常(workaround) |
| nav-matrix.json | 10 選單 × 點擊前後 URL/展開矩陣 + 鍵盤 + 行動 |
| rbac-results.json | RBAC 78 格 + 500 清單 + 400 樣本 |
| design-before-after-{nav,dashboard,table}.png | 三組現況 vs 提案對比 |
| mockups/*.html | 三個自足式設計 mockup(可直接開) |
| light-dashboard.png / dark-dashboard.png / light-sales.png / mobile-sales.png | 現況基準截圖 |
| guest-ledgerManualEntry.png | guest gating 正例(權限提示) |
| probe-dashboard-loading-700ms.png | 載入狀態探針 |
| misc-login-first-visit-overlay.png | 首訪 onboarding overlay |

## 附錄 B. 拋棄式測試腳本(不入庫;位於測試 session scratchpad `campaign/scripts/`)

| 腳本 | 用途 | 重建要點 |
|---|---|---|
| nav-probe.mjs | ERP-001 取證(點擊矩陣/鍵盤/行動/截圖/console) | chromium 由 `createRequire('<repo>/frontend/')` 解析 `@playwright/test`;登入用 demo 快速鍵 `getByRole('button',{name:'admin',exact:true})`;先寫 localStorage `erp.onboarding={completed:true,currentStep:0}` 繞過導覽 |
| api-sweep.mjs | 6 帳號 × 38 GET + null 掃描 + 401/403/404 探針 | GET 清單取自 `frontend/scripts/capture-fixtures.mjs` + `/api/masterdata/locations`、`/api/payments`、`/api/audit` |
| compare-spec.mjs | spec 三方比對(paths set + canonical deep equal) | 比對時忽略 `servers`;types 以 `npx openapi-typescript` 產到 scratch,**勿跑 `npm run gen:api`**(會覆寫 repo) |
| rbac-matrix.mjs | RBAC 78 格(全 `{}` body;403=拒,400/422/500=已過授權) | 真值表照抄 `SecurityConfig.java:79-98`,首條吻合優先 |
| flow-smoke.mjs | O2C 寫入流程 + 重複 confirm 探針 + 對帳檢查(僅本機) | taxRateCode 可省略(預設 STANDARD);FG 挑 items-status 非 OUT 者 |
| ui-shots.mjs / axe-detail.mjs / shoot-mockups.mjs | 截圖矩陣/探針/axe 明細/mockup 對比合成 | 合成頁須寫成實體 HTML 再 `goto`(`setContent` 下 file:// 圖片會被擋);axe 用 CDN `axe-core@4.10.2` |

---
*報告產生:2026-07-02 測試戰役(Claude Code)。測試期間未修改任何程式;repo 僅新增本報告與 test-evidence。*
