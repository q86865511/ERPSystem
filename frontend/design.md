# 前端視覺規範 — 墨青帳房(Ink Ledger)

> 本文件是 ERP 前端的美術唯一依據(single source of truth)。之後所有 UI 修改、
> 新頁面、新元件都應回到這裡對齊;若需偏離,先修改本文件再改程式碼。
> 取代對象:現行 Blue Enterprise(`#2563EB` 藍 + cool slate 灰)。

---

## 1. 設計哲學與氛圍定位

**一句話**:把台灣商業文化裡「帳簿與印鑑」的信任感,翻譯成現代數位工具。

這套 ERP 服務的是每天面對訂單、庫存、傳票的企業使用者。視覺的任務不是炫技,
而是傳達「這裡的數字可靠、這張單據已被負責地蓋章確認」。因此:

- **主體是墨**:深墨青綠(近墨色而帶青)承擔所有主要動作與導覽,沉穩、不搶戲。
- **紙是底**:頁面是宣紙暖白,卡片是純白;層次靠「紙色差 + 細邊框」,不靠陰影堆疊。
- **朱印只做一件事**:朱印紅是全系統唯一的高彩度色,**只**用於「印章」語彙
  (審核/過帳狀態章、危險動作)。它的稀有性就是它的力量。
- **標題有帳房氣質**:中文襯線(Noto Serif TC)只出現在標題與關鍵數字,
  內文維持無襯線的現代效率感。

**簽名元素(全系統唯一記憶點)**:朱印圓章 `SealBadge` —— 單據的審核狀態以
圓形雙框、微旋轉的朱印呈現,審核通過瞬間有「蓋章落下」的微動效。
除此之外的一切都保持安靜與紀律。

---

## 2. 色彩系統

### 2.1 主色 ramp — `ink`(取代現行 `brand` 藍色 ramp)

Mantine 10 階,錨點為 `#123F3C`(shade 8)。

```ts
const ink: MantineColorsTuple = [
  '#EBF2F0', // 0  最淡水洗(hover wash、striped)
  '#D4E4E1', // 1
  '#B0CCC7', // 2
  '#8AB3AC', // 3  青瓷(sidebar active bar)
  '#67998F', // 4  dark-mode primary
  '#477F73', // 5
  '#2F6559', // 6
  '#1D5049', // 7  hover(light)
  '#123F3C', // 8  primary(light)── 錨點「墨青」
  '#0C2B29', // 9
];
// createTheme: primaryColor: 'ink', primaryShade: { light: 8, dark: 4 }
```

### 2.2 點睛色 ramp — `seal`(朱印紅)

```ts
const seal: MantineColorsTuple = [
  '#FBEEEB', // 0
  '#F4D4CD', // 1
  '#E9B0A5', // 2
  '#DC8A7B', // 3
  '#D06853', // 4  dark-mode 朱印/danger
  '#C43D2F', // 5  朱印錨點(light)
  '#A93225', // 6  hover
  '#8C291E', // 7
  '#6F2018', // 8
  '#521711', // 9
];
```

**使用紀律**(最重要的一條規則):
- 朱印「**造型**」(圓形雙框章)只用於單據審核/過帳狀態與蓋章動效。
- 朱印「**色相**」同時兼任 danger 語意色(刪除鈕、錯誤、逾期警示文字)。
- 除上述之外,任何裝飾、圖示、強調**都不得**使用 seal ramp。
  一個畫面上朱紅出現超過兩處,就是用錯了。

### 2.3 中性 ramp — `gray`(暖石灰,取代 cool slate)

```ts
const paperGray: MantineColorsTuple = [
  '#FAF9F6', // 0  宣紙白(light 頁面底)
  '#F2F0EA', // 1  表頭底、次級面
  '#E5E2D9', // 2  light 邊框
  '#CFCCC2', // 3
  '#A6A69C', // 4  dimmed 文字(light)
  '#77786F', // 5
  '#4A4D46', // 6
  '#343A36', // 7
  '#1C2321', // 8  主文字(light)/ dark 卡片參考
  '#121917', // 9  dark 頁面
];
```

### 2.4 表面 CSS 變數(index.css)

```css
:root, [data-mantine-color-scheme='light'] {
  --mantine-color-body: #FAF9F6;   /* 宣紙白頁面 */
  --app-color-card:     #FFFFFF;   /* 卡片純白 */
  --app-color-border:   #E5E2D9;   /* 暖細框 */
}
:root[data-mantine-color-scheme='dark'], [data-mantine-color-scheme='dark'] {
  --mantine-color-body: #121917;   /* 帶青的墨黑 */
  --app-color-card:     #1D2724;
  --app-color-border:   #2E3A35;
}
```

側欄(AppShell navbar)是**固定墨青**、不隨色彩模式翻轉:
light 用 `#123F3C`,dark 微降明度用 `#0F332F`。這讓品牌色在兩種模式下都在場。

### 2.5 語意色(沿用現行 AA 紀律,換色相)

| token | light | dark | 用途 |
|---|---|---|---|
| `--erp-positive-text` | `#0A5C42` | `#63C9A8` | 正向增減、已完成 |
| `--erp-positive-bg`   | `#E6F5EE` | `#0B3B2E` | 正向 wash |
| `--erp-warning-text`  | `#A5480A` | `#F0B478` | 警示 |
| `--erp-negative-text` | `#A93225`(seal-6) | `#E08A7A` | 負向、錯誤、逾期 |
| `--erp-negative-bg`   | `#FBEEEB`(seal-0) | `#3B1512` | 負向 wash |

所有語意色在其 wash 與白/暗底上必須維持 WCAG AA(≥ 4.5:1);
遷移實作時逐一以對比工具驗證,不得只憑目測。

### 2.6 圖表配色(@mantine/charts)

從墨青/紙色系延伸的低飽和文化色,取代現行藍系 palette:

```css
--erp-chart-1: #2F6559;  /* 墨青(主系列) */
--erp-chart-2: #4E7CA1;  /* 靛藍 */
--erp-chart-3: #C99235;  /* 藤黃 */
--erp-chart-4: #A8574A;  /* 磚朱(非 seal 錨點,避免搶章) */
--erp-chart-5: #7B6BA8;  /* 紫藤 */
--erp-chart-6: #5E9E8F;  /* 青瓷綠 */
--erp-chart-7: #A96C8E;  /* 牡丹 */
--erp-chart-8: #8A8B5C;  /* 苔綠 */
```

dark 模式至少覆寫 `--erp-chart-1: #5FA396`(墨青在暗底對比不足),
其餘系列遷移時逐一檢查暗底對比,不足者提亮一階。

---

## 3. 字體

| 角色 | 字族 | 備註 |
|---|---|---|
| 標題/關鍵數字(display) | `'Noto Serif TC', 'Source Serif 4', Georgia, serif` | 只用 600/700 兩個字重 |
| 內文(body) | `'Plus Jakarta Sans', -apple-system, 'Segoe UI', 'PingFang TC', 'Microsoft JhengHei', 'Noto Sans TC', sans-serif` | 沿用現行自架 latin woff2 |
| 等寬(單號/代碼) | `'JetBrains Mono', ui-monospace, monospace` | 沿用 |

**規則**:
- 襯線只給 `h1–h4` 與 KPI 大數字;`h5/h6`、表格、表單、按鈕一律無襯線。
- 所有金額與數量:`font-variant-numeric: tabular-nums`,金額**一律右對齊 + 千分位**。
- 層級(概略沿用現行尺寸,調整字重歸屬):

```
h1 28px/700 serif · h2 24px/700 serif · h3 20px/600 serif · h4 17px/600 serif
h5 15px/500 sans  · h6 13px/500 sans
body 14px/400 · caption 12px · KPI 數字 21–24px/700 serif
行高:標題 1.35、內文 1.55
```

**Noto Serif TC 載入策略**:採 Google Fonts 的 unicode-range 分片 woff2
(僅按頁面實際字元下載,傳輸量小)。Docker 離線 demo 需自架時,
下載 chinese-traditional 分片全集打進 image(數 MB 等級,可接受),
比照現行 `PlusJakartaSans-latin.woff2` 的自架模式。

---

## 4. 空間、圓角、邊框、陰影

```ts
defaultRadius: 'md',
radius:  { xs: '3px', sm: '6px', md: '8px', lg: '12px', xl: '16px' },
spacing: { xs: '8px', sm: '12px', md: '16px', lg: '24px', xl: '32px' },  // 沿用
```

- **邊框**:一律 `1px solid var(--app-color-border)`。層次以「頁(宣紙)→ 卡(白)→
  表頭(gray-1)」的紙色差表達,不用第二層邊框。
- **陰影**:比現行更輕。卡片預設 **無陰影**(`withBorder` 即可);
  只有浮出層(Modal / Drawer / Popover / Menu)用 `md` 陰影。
  現行 xs–xl 五階保留定義,但 xs/sm 僅供 hover 提起等過渡狀態。
- **留白節奏**:頁面外距 `lg`(24px);卡片內距 `md`–`lg`;
  卡片之間 `sm`(12px);段落區塊之間 `lg`。表格列高 ≥ 40px,寧鬆勿擠。

---

## 5. 元件規範(Mantine 對應,與現行 theme.ts 的差異)

| 元件 | 規範 | 相對現行的變化 |
|---|---|---|
| `AppShell` | header:白/墨卡面;**navbar:固定墨青 `#123F3C`**(dark `#0F332F`),文字 `#DCE7E2` | navbar 從淺色翻為深墨青,最大的結構性改變 |
| `NavLink`(側欄內) | active:`3px` 左條 **青瓷 `ink-3`** + `rgba(138,179,172,.16)` wash + 白字;inactive `#A9C2BA` | 左條由 brand-6 藍改青瓷;深底配色全部新寫 |
| `Button` | filled = `ink-8`,hover `ink-7`;radius `md`;danger 一律 `seal` filled | 換色;每畫面 filled 主鈕 ≤ 1 顆 |
| `Card` | 白底、`1px` 暖框、radius `lg`(12px)、**shadow 無** | 由 `shadow: xs` 改為無陰影 |
| `Paper` | 同 Card,不強制 radius | shadow 移除 |
| `Table` | th:底 `gray-1 #F2F0EA`、字 `ink-8`、weight 500;striped `#FAF9F6`;hover `ink-0`;金額欄右對齊 tabular-nums | th 從 brand wash 改宣紙灰;新增金額欄排版規則 |
| `Badge` | 一般狀態:`variant='light'` 沿用;**單據審核狀態不用 Badge,用 `SealBadge`(見 §6)** | 新增單據狀態的專屬元件 |
| `Tabs` | active 底線 `ink`,字 `ink-8` | 換色 |
| `Modal` / `Drawer` | 白面、radius `lg`、`shadow: md`;close 鈕 `ink-8`(**不再**用 accent 色) | close 鈕由 brand-6 改墨青 |
| `Input` 系 | 靜置框 `--app-color-border`;focus 框 `ink-8`(隨 primary) | 換色即可,結構沿用 |
| `ActionIcon` | `subtle` 預設沿用;刪除類 inline `color='seal'` | 換色 |
| `Alert` | `variant='light'` 沿用;error 用 seal wash | 換色 |
| `Tooltip` | `color='dark'` 沿用 | 不變 |

新頁面一律先用上表的預設值,只有該頁有明確理由時才 inline 覆寫。

---

## 6. 簽名元件:`SealBadge`(朱印章)

單據(訂單/採購單/傳票…)的審核與過帳狀態專用:

- **造型**:圓形雙框(外框 `1.5px` + 內框 `1px`,間距 2px)、直徑 36–40px、
  旋轉 `-8deg`、文字 `Noto Serif TC 700 12px`。
- **尺寸**:列表儲存格內使用等比精簡章(直徑約 28px),詳情頁狀態區維持 36–40px
  完整章,避免同表列高不一致。
- **狀態對應**:
  - `已審` / `已核准` → 朱印(`seal-5`,dark 用 `seal-4`)
  - `已過帳` / `已結案` → 墨印(`ink-8` filled 圓角矩形章,不旋轉)
  - `待審` → 灰框 chip(`gray-3` 框、`gray-5` 字)——**沒有章,因為還沒蓋**
  - `草稿` → `subtle` 灰
- **非 CJK 標籤**:章內文字為非中日韓短標籤(如英文 locale)時,朱印改用同色、
  同旋轉的雙框圓角矩形章,寬度隨文字自適應;圓形雙框章保留給 ≤3 字的 CJK 標籤。
- **蓋章動效**:審核通過的瞬間,章以 `scale(1.2)→1 + rotate(-12deg→-8deg)`、
  `180ms ease-out` 落下;`prefers-reduced-motion` 時直接出現。
- **紀律**:SealBadge 只出現在狀態欄位與詳情頁的狀態區,不作清單裝飾、
  不作空狀態插圖、不作 logo。

---

## 7. 動效原則

- 預設過渡 `150ms ease-out`(hover、focus、色彩),抽屜/彈窗 `200ms`。
- 全系統**唯一**的表演性動效就是蓋章(§6);不加頁面級進場動畫、不加視差。
- 一律尊重 `prefers-reduced-motion: reduce`。

---

## 8. Do / Don't

**Do**
- 金額右對齊 + 千分位 + tabular-nums,永遠。
- 每個畫面一顆 filled 主鈕;其餘用 `default` / `subtle`。
- 用紙色差(宣紙頁 → 白卡 → 灰表頭)表達層次。
- 中文標題用襯線,但只到 h4;資料層永遠無襯線。

**Don't**
- 朱紅不做裝飾(分隔線、icon 點綴、圖表主色都不行)。
- 卡片不堆陰影;需要更多層次時先檢討資訊架構,而不是加陰影。
- 不引入第三個彩度色相;圖表 palette(§2.6)是唯一例外且僅限圖表。
- 不在深墨青側欄上放低對比的灰字(< 4.5:1 一律禁止)。

---

## 9. 遷移備註(從 Blue Enterprise 切換)

1. **命名策略**:`theme.ts` 的 ramp 名 `brand` 直接改指墨青(`brand = ink`),
   全 codebase 的 `brand-*` / `--mantine-color-brand-*` 引用**不需改名**,
   換 ramp 內容即可;`seal` 為新增 ramp。
2. **主要觸點**(依據現行結構):
   - `frontend/src/theme.ts`:兩個 ramp 內容、`primaryShade { light: 8, dark: 4 }`、
     radius/shadow/headings/fontFamily、§5 全部 component overrides。
   - `frontend/src/index.css`:表面變數(§2.4)、語意色(§2.5)、圖表變數(§2.6)、
     `@font-face` 新增 Noto Serif TC。
   - `AppShell` navbar 深色化會牽動側欄相關元件(NavLink、logo 區、
     collapse 控制)的前景色,需一起改。
   - `SealBadge` 為新元件,建議放 `frontend/src/components/`,
     替換各單據列表/詳情頁現用的狀態 Badge。
3. **暗色模式**:現行 `--mantine-color-dimmed` 的 AA 修正邏輯保留,
   數值依新暗面(`#1D2724`)重新驗證。
4. **CSP / 離線**:字體維持自架原則(§3),不得新增 runtime CDN 依賴
   (參照 Docker demo 與 CSP 的既有教訓)。
5. **驗證關卡**:每個改版 PR 需附 light + dark 截圖,並過一次
   對比檢查(語意色、側欄文字、表頭文字)。

---

*版本:v1.0(2026-07-03)· 方向定案:墨青帳房(自三提案 mockup 中選定)*
