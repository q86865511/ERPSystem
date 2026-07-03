# ERP Copilot MCP Server

獨立的 [Model Context Protocol](https://modelcontextprotocol.io/) stdio server，把 ERP 系統的工具（讀取報表、建立銷售訂單等）暴露給 Claude Desktop、Claude Code 等 MCP client。

這個套件**不掛進 frontend build**，是一個獨立的 Node.js/TypeScript 專案，只在需要用 MCP client 操作 ERP 時才啟動。

## 設計重點

- **單一事實來源**：工具清單讀取 repo 既有的 `src/main/resources/assistant/tools.json`（與 Spring Boot 後端的
  in-app assistant 共用同一份 manifest），不重複定義工具。新增/修改工具只需改那一份 JSON。
- **登入方式**：用 `ERP_USERNAME` / `ERP_PASSWORD` 呼叫 `POST /api/auth/login` 換取 access token（15 分鐘
  TTL），存在記憶體。呼叫任何工具若收到 401，會自動重新登入一次再重試——不模擬 refresh cookie 流程。
  Access token 只存在這個 Node 行程的記憶體裡，不落地。
- **寫入確認交給 MCP client**：`tools.json` 裡每個工具只記錄 `kind`（`read`/`write`），MCP 的
  `annotations`（`readOnlyHint`/`destructiveHint`/`idempotentHint`）是這個 server 在 `ListTools` 回應時
  依 `kind` 衍生出來的，不是 manifest 本身的欄位。`create_sales_order` 這類 write 工具會被標成
  `readOnlyHint: false, destructiveHint: false, idempotentHint: false`，觸發 Claude Desktop / Claude Code
  內建的「是否允許執行這個動作」確認 UX。本 server 本身**不會**再做一層確認——因為 server 端沒有使用者身份
  可以對話確認，重複做只是多一層跟 client UX 不一致的假關卡。若要停用某個 write 工具，請直接從
  `tools.json` 移除或調整 `requiredRoles`，而不是在這裡加邏輯。

## 安裝與建置

```bash
cd mcp-server
npm install
npm run build   # tsc -> dist/
npm test        # vitest（mock fetch，不打網路）
npm start       # 需要先設定下面的環境變數
```

## 環境變數

| 變數 | 說明 | 範例 |
|---|---|---|
| `ERP_BASE_URL` | ERP 後端的 base URL | `http://localhost:8081` |
| `ERP_USERNAME` | 登入帳號 | `admin` |
| `ERP_PASSWORD` | 登入密碼 | `admin` |
| `ERP_TOOLS_MANIFEST`（選填） | 覆寫 tools.json 路徑；預設從 repo 相對路徑（`../src/main/resources/assistant/tools.json`）載入,只有打包部署、脫離 monorepo 結構時才需要覆寫 | `/opt/erp-mcp/tools.json` |

## 接上 Claude Desktop

先 `npm run build` 產生 `dist/index.js`，再編輯 `claude_desktop_config.json`（macOS：
`~/Library/Application Support/Claude/claude_desktop_config.json`；Windows：
`%APPDATA%\Claude\claude_desktop_config.json`），加入：

```json
{
  "mcpServers": {
    "erp": {
      "command": "node",
      "args": ["<abs path>/mcp-server/dist/index.js"],
      "env": {
        "ERP_BASE_URL": "http://localhost:8081",
        "ERP_USERNAME": "admin",
        "ERP_PASSWORD": "admin"
      }
    }
  }
}
```

`<abs path>` 換成這個 repo 在你機器上的絕對路徑。重啟 Claude Desktop 後即可在對話中使用 ERP 工具。

## 接上 Claude Code

```bash
claude mcp add erp -- node <abs path>/mcp-server/dist/index.js
```

需要另外設定環境變數的話（依 `claude mcp add` 版本可能有 `--env` 參數，或直接編輯生成的設定檔），確保
`ERP_BASE_URL`、`ERP_USERNAME`、`ERP_PASSWORD` 三者都有值——缺一個 server 啟動就會丟錯退出。

## 安全注意事項

- **帳密只放在環境變數**：不要把 `ERP_USERNAME` / `ERP_PASSWORD` 寫進任何會被 commit 的檔案；
  `claude_desktop_config.json` 通常不在版控範圍，但仍建議只用 demo/測試帳號。
- **這是 demo/開發用途的帳密模式**：目前沒有做 OAuth device flow 或個別使用者憑證交換，MCP server
  用同一組帳密代表「這個 server 實例」，所有透過它的操作都算在這個帳號名下。正式環境要接 MCP，建議先
  評估是否需要換成每位使用者各自的憑證。
- **write 工具的確認完全依賴 MCP client**：server 一律照 client 傳來的呼叫執行。如果你接的 client 不會對
  `readOnlyHint: false` 的工具做確認 UX，寫入動作就會直接執行——請先確認你使用的 client 有落實 MCP
  annotations 的確認流程。
