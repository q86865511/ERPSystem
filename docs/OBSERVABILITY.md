# 可觀測性 · Observability

後端以 **Micrometer + Prometheus** 暴露指標、以 **結構化日誌 + 關聯 ID** 串起每筆請求,並附一套可選的 **Prometheus + Grafana** 觀測堆疊。設計原則:**指標不入侵業務**(複用既有 domain event)、**端點不公開**(`/actuator/prometheus` 只在內部網路可達)、**標籤有界**(避免 cardinality 爆炸)。

## 指標(`/actuator/prometheus`)

`management.endpoints.web.exposure.include` 僅開放 `health,info,prometheus`(**絕不用 `*`** —— 會洩漏 `env`/`configprops` 內的 JWT secret 與 DB 密碼)。

### 業務指標(由既有 AFTER_COMMIT 事件衍生)
`com.erp.observation.application.MetricsEventListener` 監聽與審計軌跡相同的事件,只記**已 commit** 的動作:

| 指標 | 型別 | 標籤(有界) | 來源事件 |
|---|---|---|---|
| `erp_journal_postings_total` | counter | `sourceDocType` | `JournalPostedEvent`(唯一過帳點 → 涵蓋全部金流) |
| `erp_auth_logins_total` | counter | `result`=success\|failure | `AuthAuditEvent` |
| `erp_fiscal_period_changes_total` | counter | `action`=closed\|reopened | `FiscalPeriodChangedEvent` |
| `erp_reconciliation_healthy` | gauge | — | `ReconciliationService`(1=帳平且子帳==GL,0=否) |

> `erp_reconciliation_healthy` 刻意是**指標而非 `HealthIndicator`** —— 若做成 health,讀到 0 會翻掉 `/actuator/health`,使 compose healthcheck 失敗、前端 `depends_on: service_healthy` 永遠起不來。Gauge 採「讀取時計算、最多每 60 秒一次」節流,Prometheus 每 15 秒抓取不會觸發整輪 DB 對帳。

> **標籤紀律**:只用有界欄位(文件型別、成功/失敗、關閉/重開)。**絕不**把 actor/username/單號/memo 當標籤 —— 每個值一條時間序列,會撐爆 meter registry 記憶體。

### 系統指標(actuator + micrometer 免費提供)
`http_server_requests_*`(含 p95 延遲)、`jvm_*`、`hikaricp_*`。共同標籤 `app="erp"`。

### 暴露與安全
`/actuator/prometheus` 在 `SecurityConfig` 為 `permitAll`,但 **frontend nginx 不反代 `/actuator`** → 公網不可達;只有同一 docker 網路內的 Prometheus 容器能以 `app:8080` 直接抓取。其餘 actuator 端點落在 `anyRequest().authenticated()`,未登入即 401。

## 結構化日誌 + 關聯 ID

- **關聯 ID**:`RequestCorrelationFilter`(最高優先序,排在 security filter chain 之前)讀取進來的 `X-Request-Id`(或產生 UUID),放進 SLF4J MDC(`correlationId`)讓每行 log 都帶,並回寫成 response header。輸入值經過 charset 淨化以防 log forging。nginx 以 `proxy_set_header X-Request-Id $request_id` 把 access log 與後端 log 串起來。
- **JSON 日誌(profile 控制)**:預設 console 為人類可讀(log 行帶 `[correlationId]`);`json` profile(見 `application-json.properties`,由觀測 overlay 啟用)改用 Spring Boot 內建 **ECS** 結構化格式,MDC 自動成為欄位。dev/測試/部署 cron.log 維持純文字。

## 跑起整套(可選 overlay)

預設的一鍵 demo **不含** Prometheus/Grafana(保持精簡)。要看完整觀測堆疊:

```bash
docker compose -f compose.demo.yaml -f compose.observability.yaml up --build
```

- Grafana：<http://localhost:3000>（admin/admin）—— 已自動掛 Prometheus 資料源 + 預載「ERP Overview」儀表板(對帳健康、過帳速率、登入、HTTP p95、JVM heap)。
- Prometheus：<http://localhost:9090>。
- overlay 同時把 app 切到 `seed,json` profile(結構化日誌)。

> 沙箱無法跑真實 web server,故本文件不含實機 Grafana 截圖;在本機/Oracle 跑上面指令即可重現,歡迎把截圖補進 `docs/`。
