# 部署(Deployment)

線上 demo:**<https://erp.terrychou.com>**(JWT 認證;預設以唯讀 `guest` 進入,試寫入用角色帳號如 `admin`/`admin`;Swagger 在 `/swagger-ui.html`)。

本專案有兩種跑法:**本機一鍵 demo**(可攜、人人可跑)與**雲端子網域部署**(目前 live 的那套)。

## 本機一鍵 demo

前置:Docker。

```bash
docker compose -f compose.demo.yaml up --build
# 前端 http://localhost:8081 ; Swagger http://localhost:8081/swagger-ui.html
```

`compose.demo.yaml` 起三個容器:`postgres`(具 named volume 與 healthcheck)、`app`(Spring Boot,啟動時以 `seed` profile 經真實 service 灌入完整 買→做→賣,並設 `SPRING_DOCKER_COMPOSE_ENABLED=false` 避免容器內又去找 compose)、`frontend`(nginx 服務 Vite 靜態檔,並反代 `/api`、`/v3/api-docs`、`/swagger-ui`、`/webjars` 到 `app:8080`)。`DataSeeder` 冪等(demo 廠商已存在則跳過),保留 volume 重跑安全;要全新資料用 `docker compose -f compose.demo.yaml down -v`。

> 本機開發(非容器)見 [README](../README.md) 的「快速開始 / 本機開發」。

## 雲端部署(Oracle Cloud + Cloudflare Tunnel + Caddy)

live demo 跑在一台 Oracle Cloud(ARM64)主機上,與作品集主站及其他專案共用同一套對外架構:

```
                         ┌─────────────── Oracle Cloud 主機(ARM64)───────────────┐
瀏覽器 ──HTTPS──▶ Cloudflare ──Tunnel──▶ cloudflared ──▶ Caddy(:8080,依 Host 路由)
  (Universal SSL          (named tunnel       (/etc/cloudflared      ├─ terrychou.com        → /srv/main(靜態)
   *.terrychou.com)        "resume")           /config.yml)          ├─ soulshard.terrychou… → /api→:8787 · 其餘 /srv/soulshard
                                                                     ├─ steam.terrychou…     → /api→:8788 · 其餘 /srv/steam
                                                                     └─ erp.terrychou.com    → reverse_proxy 127.0.0.1:8081
                                                                                                 └─▶ 前端容器(nginx)
                                                                                                       ├─ 靜態 SPA
                                                                                                       └─ /api、/swagger-ui… → app:8080 → postgres
```

- **HTTPS** 由 Cloudflare 終結(Universal SSL 覆蓋 `*.terrychou.com`);源站不需自管憑證。
- **cloudflared**(Cloudflare Tunnel,named tunnel)從主機**對外撥出**連到 Cloudflare,把各 hostname 導到本機 `127.0.0.1:8080`;不需在雲端防火牆開任何入站埠。
- **Caddy**(`http_port 8080`、`auto_https off`、`bind 127.0.0.1`)依 Host 把各子網域路由到對應後端 / 靜態目錄。
- **ERP** 採「整包容器」變體:Caddy 反代到前端容器(`127.0.0.1:8081`),容器內 nginx 再服務 SPA 並反代 `/api` 到後端容器 —— 與 `compose.demo.yaml` 同一套,部署即「clone + compose up」。

### 上一個新子網域(以 erp 為例)

```bash
# 1) 起 demo,前端只綁 localhost(只給 Caddy 接,不直接對外)
#    用 compose.oracle.yaml overlay 把 frontend port !override 成 127.0.0.1:8081(不必再手動 sed)
git clone https://github.com/q86865511/ERPSystem.git ~/erp-demo-test && cd ~/erp-demo-test
docker compose -f compose.demo.yaml -f compose.oracle.yaml up --build -d

# 2) Caddy 加一個子網域 block(/etc/caddy/Caddyfile),驗證後 reload
#    http://erp.terrychou.com { bind 127.0.0.1; encode zstd gzip; reverse_proxy 127.0.0.1:8081 }
sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl reload caddy

# 3) cloudflared 加 ingress(/etc/cloudflared/config.yml,放在 404 catch-all 之前)
#    - hostname: erp.terrychou.com
#      service: http://127.0.0.1:8080
sudo systemctl restart cloudflared
cloudflared tunnel route dns resume erp.terrychou.com   # 建立 Cloudflare DNS CNAME
```

> 改 Caddy / cloudflared 設定前先 `cp` 備份;`caddy validate` 通過再 reload。新子網域的 TLS 由 `*.terrychou.com` 的 Universal SSL 自動覆蓋。

### 自動部署(merge 到 main → 自動上線)

merge 到 `main` 且 CI 綠後,`.github/workflows/deploy.yml`(`workflow_run` 觸發)會 SSH 進 Oracle 觸發重新部署。設計重點:

- **只在 main 的真實 push + CI 成功**才部署(`if` 同時檢查 `conclusion==success`、`head_branch==main`、`event==push`,擋掉 fork PR 的 CI 也會發 `workflow_run`)。`workflow_run` 永遠執行 default branch 上的 workflow 定義,所以 fork 改不動部署流程。
- **受限部署金鑰**:authorized_keys 用 forced command 把這把專用 ed25519 金鑰鎖死成「只能跑部署 wrapper、拿不到 shell」,即使 GitHub secret 外洩,攻擊者也只能觸發「從 main 重新部署」,動不了這台共用主機的其他東西。
- GitHub `production` environment 下的 secrets:`ORACLE_DEPLOY_KEY`(私鑰)、`ORACLE_HOST`、`ORACLE_USER`、`ORACLE_KNOWN_HOSTS`(`ssh-keyscan` 輸出,pin host key)。deploy job 設 `permissions: {}`、`concurrency: deploy-oracle`(序列化避免併發互踩)。
- 實際 build 在 Oracle 上做(ARM 原生);workflow 本身不 build/push image。

部署 wrapper(`scripts/oracle-deploy.sh.example` 的內容,在 Oracle 端 `cp` 成 **repo 外** 的 `~/erp-demo-deploy.sh`)做:`git fetch + reset --hard origin/main` → `docker compose -f compose.demo.yaml -f compose.oracle.yaml up -d --build --remove-orphans` → `docker image prune -f`(僅 dangling)。放 repo 外是為了避免 `git reset` 把正在執行的腳本檔換掉。log 在 `~/backups/erp-demo/cron.log`。

> 加速方向(future):改由 CI build & push image 到 GHCR,Oracle 端只 `pull` 不 `build`(目前規模用本機 `--build` + layer cache 已足夠,故未做)。

### 每晚自動重置

公開 demo 預設唯讀 guest,但角色帳號(admin 等)仍可寫入並累積測試資料。`scripts/reset-demo.sh` 由 cron 每晚跑 `down -v` + `up`(清 `pgdata` → app 在空庫上重新 seed 乾淨的 買→做→賣)。`down -v` 與 `up` **都帶兩個 `-f`**(漏 overlay 會讓 frontend 變回對外裸奔)。`down -v` 是 project-scoped(只動 `erp-demo`,不誤傷共用主機其他專案)。

```cron
# 05:00 Asia/Taipei = 21:00 UTC(機器為 UTC),避開 04:30 UTC 的 soulshard 備份
0 21 * * * bash /home/ubuntu/erp-demo-test/scripts/reset-demo.sh >> /home/ubuntu/backups/erp-demo/cron.log 2>&1
```

重置期間有約 1.5–2.5 分鐘短暫不可用(postgres+app 重啟 + app healthcheck `start_period 60s` + seed)。

### Oracle 端一次性設定(已套用)

```bash
mkdir -p ~/backups/erp-demo

# 部署 wrapper 放 repo 外(避免 git reset self-modification)
cp ~/erp-demo-test/scripts/oracle-deploy.sh.example ~/erp-demo-deploy.sh && chmod 700 ~/erp-demo-deploy.sh

# 受限部署公鑰加進 authorized_keys(command= 用「絕對路徑」,它不展開 $HOME)
# command="/home/ubuntu/erp-demo-deploy.sh",no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding ssh-ed25519 AAAA... erp-deploy

# 把部署目錄從舊的 sed 改動遷成 overlay(乾淨對齊 origin/main)
cd ~/erp-demo-test && git checkout -- compose.demo.yaml && git fetch origin main && git reset --hard origin/main

# 驗證 overlay 合併:ports 應只剩 127.0.0.1:8081:80
docker compose -f compose.demo.yaml -f compose.oracle.yaml config | grep -i -A3 ports

# JWT secret(compose.oracle.yaml 用 ${APP_JWT_SECRET};compose 自動載入此 .env,且 git reset 不碰未追蹤檔)
printf 'APP_JWT_SECRET=%s\n' "$(openssl rand -base64 48)" > ~/erp-demo-test/.env && chmod 600 ~/erp-demo-test/.env
```

### demo 注意事項

公開 demo 預設以唯讀 `guest` 進入(只能讀,所有寫入 403);要試寫入用角色帳號(如 `admin`/`admin`)。後端強制 **借=貸** 與 **子帳==GL 控制科目** 不變量,所以對帳健康檢查恆為綠 —— 訪客動不壞帳本不變量,最多累積測試資料,且每晚 cron 會自動 `down -v` 重置(見上)。
