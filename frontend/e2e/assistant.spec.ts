import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, test, type Page } from '@playwright/test';

/**
 * Assistant side-panel regression (project `local`): same offline, fixture-mocked shell as e2e/nav.spec.ts
 * (built dist, /api mocked, auth faked via /api/auth/refresh). Here we additionally mock
 * /api/assistant/status and stream a canned SSE body from /api/assistant/chat so the whole HITL loop
 * (text → read tool → write proposal → confirm → resume → success) is exercised without a backend.
 */
const fixtures = JSON.parse(
  readFileSync(fileURLToPath(new URL('../scripts/fixtures/api-fixtures.json', import.meta.url)), 'utf8'),
);
const AUTH = fixtures.auth ?? { accessToken: 'demo', username: 'admin', roles: ['ADMIN'] };
const GET: Record<string, unknown> = fixtures.get ?? {};

/** Assembles an SSE body string from [event, dataObject] pairs. */
function sseBody(frames: Array<[string, unknown]>): string {
  return frames.map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join('');
}

// First turn: assistant text, a read tool that succeeds, then a write proposal that pauses the stream.
const TURN_1 = sseBody([
  ['text_delta', { text: 'Let me check that for you. ' }],
  ['tool_call', { id: 'r1', name: 'list_items', input: { q: 'widget' }, kind: 'read' }],
  ['tool_result', { id: 'r1', ok: true, result: '[{"sku":"W-1"}]' }],
  ['tool_call', { id: 'w1', name: 'create_purchase_order', input: { partnerId: 7, qty: 5 }, kind: 'write' }],
  ['awaiting_confirmation', { id: 'w1', name: 'create_purchase_order', input: { partnerId: 7, qty: 5 } }],
  ['done', { stopReason: 'awaiting_confirmation', usage: { inputTokens: 10, outputTokens: 20 } }],
]);

// Second turn (after confirm): the write tool result + a closing message.
const TURN_2 = sseBody([
  ['tool_result', { id: 'w1', ok: true, result: '{"poId":42}' }],
  ['text_delta', { text: 'Purchase order created.' }],
  ['done', { stopReason: 'end_turn', usage: { inputTokens: 30, outputTokens: 15 } }],
]);

/** Chat POST request bodies captured in call order, for asserting the resume request's shape. */
type ChatCall = {
  messages: Array<{ role: string; content: Array<Record<string, unknown>> }>;
  decision?: unknown;
  preset?: string;
};

async function mockApi(
  page: Page,
  opts: { enabled: boolean },
): Promise<{ chatCalls: ChatCall[] }> {
  await page.addInitScript(() => {
    localStorage.setItem('erp.locale', 'en');
    localStorage.setItem('erp.onboarding', JSON.stringify({ completed: true, currentStep: 99 }));
  });
  // Playwright gives the LAST-registered matching route precedence, so register the generic catch-all
  // first and the specific assistant routes after it.
  await page.route('**/api/**', async (route) => {
    const req = route.request();
    const p = new URL(req.url()).pathname;
    const json = (b: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(b) });
    if (p === '/api/auth/refresh' || p === '/api/auth/login') return json(AUTH);
    if (req.method() === 'GET') return json(Object.prototype.hasOwnProperty.call(GET, p) ? GET[p] : []);
    return json({});
  });
  // Status flag.
  await page.route('**/api/assistant/status', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ enabled: opts.enabled }) }),
  );
  // Chat: first POST returns TURN_1 (paused for confirmation), the resume POST returns TURN_2. Every
  // request body is captured (in call order) so tests can assert exactly what the resume POST carried.
  const chatCalls: ChatCall[] = [];
  let call = 0;
  await page.route('**/api/assistant/chat', (route) => {
    chatCalls.push(route.request().postDataJSON() as ChatCall);
    const body = call === 0 ? TURN_1 : TURN_2;
    call += 1;
    return route.fulfill({ status: 200, contentType: 'text/event-stream', body });
  });
  return { chatCalls };
}

async function enterApp(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('button', { name: /admin/i })).toBeVisible();
}

test.describe('assistant side panel (ERP Copilot PR3)', () => {
  test('runs the full HITL loop: text → read tool → write confirm → success', async ({ page }) => {
    const { chatCalls } = await mockApi(page, { enabled: true });
    await enterApp(page);

    // Header entry renders (status enabled) and opens the drawer.
    await page.getByRole('button', { name: 'Open assistant' }).click();
    const drawer = page.getByRole('dialog', { name: 'ERP Copilot' });
    await expect(drawer).toBeVisible();

    // Send a message.
    await drawer.getByRole('textbox').fill('Order 5 widgets');
    await page.getByRole('button', { name: 'Send' }).click();

    // Streamed assistant text arrives.
    await expect(drawer.getByText('Let me check that for you.')).toBeVisible();

    // Read tool card resolves to done.
    await expect(drawer.getByText('list_items')).toBeVisible();

    // Write proposal surfaces with confirm/reject.
    await expect(drawer.getByText('create_purchase_order')).toBeVisible();
    const confirm = drawer.getByRole('button', { name: 'Confirm' });
    await expect(confirm).toBeVisible();

    // Confirm resumes the stream (second POST) → success message.
    await confirm.click();
    await expect(drawer.getByText('Purchase order created.')).toBeVisible();

    // Exactly two chat POSTs: the original turn and the confirm-driven resume.
    expect(chatCalls).toHaveLength(2);
    const resumeBody = chatCalls[1];

    // The resume carries the write decision, approved.
    expect(resumeBody.decision).toEqual({ toolUseId: 'w1', approved: true });

    // The resume's history includes this turn's already-completed read tool_result (r1) — not just the
    // write's tool_use — so the backend can replay the full turn.
    const allBlocks = resumeBody.messages.flatMap((m) => m.content);
    const r1Result = allBlocks.find(
      (b) => b.type === 'tool_result' && b.toolUseId === 'r1',
    );
    expect(r1Result).toMatchObject({ type: 'tool_result', toolUseId: 'r1', content: '[{"sku":"W-1"}]' });

    // No duplicate assistant tool_use block: each of r1/w1's tool_use appears exactly once across the
    // resume history (the bug this suite guards against would have pushed the assistant turn twice).
    const toolUseBlocks = allBlocks.filter((b) => b.type === 'tool_use');
    const toolUseIds = toolUseBlocks.map((b) => b.id);
    expect(new Set(toolUseIds).size).toBe(toolUseIds.length);
    expect(toolUseIds.sort()).toEqual(['r1', 'w1']);
  });

  test('shows no assistant entry when the feature is disabled', async ({ page }) => {
    await mockApi(page, { enabled: false });
    await enterApp(page);
    await expect(page.getByRole('button', { name: 'Open assistant' })).toHaveCount(0);
  });
});

// PR4: "why" analysis presets — starter chips shown on an empty conversation that kick off a chat tagged
// with a preset (so the backend swaps in its reconciliation/margin system prompt).
const PRESET_TURN = sseBody([
  ['text_delta', { text: 'The books balance.' }],
  ['done', { stopReason: 'end_turn', usage: { inputTokens: 5, outputTokens: 5 } }],
]);

test.describe('assistant analysis preset chips (ERP Copilot PR4)', () => {
  test('clicking the reconciliation chip starts a conversation tagged with that preset', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('erp.locale', 'en');
      localStorage.setItem('erp.onboarding', JSON.stringify({ completed: true, currentStep: 99 }));
    });
    await page.route('**/api/**', async (route) => {
      const req = route.request();
      const p = new URL(req.url()).pathname;
      const json = (b: unknown) =>
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(b) });
      if (p === '/api/auth/refresh' || p === '/api/auth/login') return json(AUTH);
      if (req.method() === 'GET') return json(Object.prototype.hasOwnProperty.call(GET, p) ? GET[p] : []);
      return json({});
    });
    await page.route('**/api/assistant/status', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ enabled: true }) }),
    );
    const chatCalls: ChatCall[] = [];
    await page.route('**/api/assistant/chat', (route) => {
      chatCalls.push(route.request().postDataJSON() as ChatCall);
      return route.fulfill({ status: 200, contentType: 'text/event-stream', body: PRESET_TURN });
    });

    await enterApp(page);
    await page.getByRole('button', { name: 'Open assistant' }).click();
    const drawer = page.getByRole('dialog', { name: 'ERP Copilot' });
    await expect(drawer).toBeVisible();

    // Both preset chips are offered on the empty conversation.
    const reconciliationChip = drawer.getByText('Reconciliation health check');
    await expect(reconciliationChip).toBeVisible();
    await expect(drawer.getByText('Gross margin analysis')).toBeVisible();

    await reconciliationChip.click();

    // The conversation started (chips gone, canned prompt echoed as the user's message) and the assistant
    // streamed its reply.
    await expect(drawer.getByText('The books balance.')).toBeVisible();
    await expect(reconciliationChip).toHaveCount(0);

    expect(chatCalls).toHaveLength(1);
    expect(chatCalls[0].preset).toBe('reconciliation');
  });
});
