# 部署(Deployment)

線上 demo:**<https://erp.terrychou.com>**(HTTP Basic:`admin` / `admin`;Swagger 在 `/swagger-ui.html`)。

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
git clone https://github.com/q86865511/ERPSystem.git ~/erp-demo && cd ~/erp-demo
sed -i 's#- "8081:80"#- "127.0.0.1:8081:80"#' compose.demo.yaml
docker compose -f compose.demo.yaml up --build -d

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

### demo 注意事項

公開 demo 為 `admin`/`admin`,任何人可登入並建立文件;但後端強制 **借=貸** 與 **子帳==GL 控制科目** 不變量,所以對帳健康檢查恆為綠 —— 訪客動不壞帳本不變量,最多累積測試資料,`down -v` 即可重置。
