// Capture real API responses from the deployed demo, to replay locally in shoot-screenshots.mjs.
//
//   node scripts/capture-fixtures.mjs        # needs network to the demo
//
// Writes scripts/fixtures/api-fixtures.json (committed) so the screenshot run is fully offline and
// deterministic afterwards. Re-run this only when you want to refresh the demo data snapshot.
//
// The access token in the login response is scrubbed before writing — the replay never validates it,
// so no real credential/JWT lands in the repo.
import { writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const BASE = process.env.CAPTURE_BASE ?? 'https://erp.terrychou.com';
const USER = process.env.CAPTURE_USER ?? 'admin';
const PASS = process.env.CAPTURE_PASS ?? 'admin';

// Every GET endpoint the 11 screenshot pages read. Add here if shoot-screenshots.mjs logs an UNMATCHED
// path, then re-capture. Path-param / drill-down endpoints are intentionally omitted (never navigated).
const GET_PATHS = [
  '/api/reporting/income-statement',
  '/api/reporting/balance-sheet',
  '/api/reporting/trial-balance',
  '/api/reporting/reconciliation',
  '/api/sales/sales-orders',
  '/api/sales/deliveries',
  '/api/sales/sales-invoices',
  '/api/sales/customer-returns',
  '/api/sales/ar-aging',
  '/api/purchasing/purchase-orders',
  '/api/purchasing/goods-receipts',
  '/api/purchasing/vendor-bills',
  '/api/purchasing/ap-aging',
  '/api/inventory/reconciliation',
  '/api/manufacturing/boms',
  '/api/manufacturing/work-orders',
  '/api/manufacturing/reorder-report',
  '/api/masterdata/items',
  '/api/masterdata/partners',
  '/api/masterdata/warehouses',
  '/api/hr/departments',
  '/api/hr/positions',
  '/api/hr/employees',
  '/api/hr/attendance',
  '/api/hr/leave-requests',
  '/api/hr/timesheets',
  '/api/hr/payroll',
  '/api/reporting/revenue-trend',
  '/api/reporting/cash-flow',
  '/api/reporting/kpi-summary',
  '/api/reporting/budget-variance',
  '/api/inventory/items-status',
  '/api/purchasing/suppliers/performance',
];

const here = dirname(fileURLToPath(import.meta.url));
const outFile = join(here, 'fixtures', 'api-fixtures.json');

async function main() {
  console.log(`[capture] base=${BASE} user=${USER}`);
  const loginRes = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  });
  if (!loginRes.ok) {
    throw new Error(`login failed: ${loginRes.status} ${await loginRes.text()}`);
  }
  const auth = await loginRes.json();
  const token = auth.accessToken;
  if (!token) throw new Error('login response had no accessToken');
  console.log(`[capture] logged in as ${auth.username} roles=${JSON.stringify(auth.roles)}`);

  const get = {};
  let ok = 0;
  for (const p of GET_PATHS) {
    try {
      const r = await fetch(`${BASE}${p}`, { headers: { authorization: `Bearer ${token}` } });
      if (!r.ok) {
        console.warn(`[capture] MISS ${p} -> ${r.status}`);
        continue;
      }
      get[p] = await r.json();
      const v = get[p];
      console.log(`[capture] ok   ${p} (${Array.isArray(v) ? `${v.length} rows` : 'object'})`);
      ok += 1;
    } catch (e) {
      console.warn(`[capture] ERR  ${p} -> ${e.message}`);
    }
  }

  // Scrub the real JWT; keep username + roles so the replayed session renders the full (admin) app.
  const scrubbedAuth = { ...auth, accessToken: 'demo-access-token' };

  await mkdir(dirname(outFile), { recursive: true });
  await writeFile(outFile, JSON.stringify({ auth: scrubbedAuth, get }, null, 2) + '\n');
  console.log(`[capture] wrote ${outFile} (${ok}/${GET_PATHS.length} endpoints)`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
