import { describe, expect, it } from 'vitest';
import {
  assistantReducer,
  initialAssistantState,
  type AssistantAction,
  type AssistantState,
} from './assistantReducer';
import type { SseAssistantEvent, UiMessage } from './types';

/** Threads a list of actions through the reducer from the initial state (or a given seed). */
function run(actions: AssistantAction[], seed: AssistantState = initialAssistantState): AssistantState {
  return actions.reduce(assistantReducer, seed);
}

const sse = (event: SseAssistantEvent): AssistantAction => ({ type: 'sse', event });

/** The assistant draft (last UI message), asserted to exist. */
function draft(state: AssistantState) {
  const last = state.messages.at(-1) as Extract<UiMessage, { kind: 'assistant' }>;
  expect(last.kind).toBe('assistant');
  return last;
}

describe('assistantReducer', () => {
  it('opens a user + assistant pair on user_send and marks streaming', () => {
    const state = run([{ type: 'user_send', id: 'm1', text: 'hello' }]);
    expect(state.streaming).toBe(true);
    expect(state.messages).toHaveLength(2);
    expect(state.messages[0]).toEqual({ kind: 'user', id: 'm1', text: 'hello' });
    expect(draft(state)).toEqual({ kind: 'assistant', id: 'm1-a', text: '', tools: [] });
    // The raw conversation carries the user text block for replay.
    expect(state.conversation).toEqual([
      { role: 'user', content: [{ type: 'text', text: 'hello' }] },
    ]);
  });

  it('accumulates text_delta into the assistant draft', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'hi' },
      sse({ type: 'text_delta', text: 'Hel' }),
      sse({ type: 'text_delta', text: 'lo!' }),
    ]);
    expect(draft(state).text).toBe('Hello!');
  });

  it('adds a read tool card as running, then resolves it to success on tool_result', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'list items' },
      sse({ type: 'tool_call', id: 't1', name: 'list_items', input: { q: 'x' }, kind: 'read' }),
    ]);
    expect(draft(state).tools[0]).toMatchObject({ id: 't1', status: 'running', kind: 'read' });

    const done = assistantReducer(state, sse({ type: 'tool_result', id: 't1', ok: true, result: '[]' }));
    expect(draft(done).tools[0]).toMatchObject({ status: 'success', result: '[]' });
  });

  it('marks a failed tool_result as error', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'go' },
      sse({ type: 'tool_call', id: 't1', name: 'do', input: {}, kind: 'read' }),
      sse({ type: 'tool_result', id: 't1', ok: false, result: 'boom' }),
    ]);
    expect(draft(state).tools[0]).toMatchObject({ status: 'error', result: 'boom' });
  });

  it('commits the draft to conversation on done(end_turn)', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'hi' },
      sse({ type: 'text_delta', text: 'Hello' }),
      sse({ type: 'done', stopReason: 'end_turn' }),
    ]);
    expect(state.streaming).toBe(false);
    expect(state.conversation).toEqual([
      { role: 'user', content: [{ type: 'text', text: 'hi' }] },
      { role: 'assistant', content: [{ type: 'text', text: 'Hello' }] },
    ]);
  });

  // The REAL backend sends NO `tool_call` for write tools — only `awaiting_confirmation` (it stops the
  // stream at the first write). The reducer must upsert the proposed ToolCard from that event alone;
  // relying on a prior write `tool_call` deadlocked the drawer in live verification (pending locks the
  // input, but no card meant no confirm/reject buttons existed anywhere).
  it('parks a write tool as proposed + pending on awaiting_confirmation alone (no prior tool_call)', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'create PO' },
      sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
      sse({ type: 'done', stopReason: 'awaiting_confirmation' }),
    ]);
    expect(state.streaming).toBe(false);
    expect(state.pending).toEqual({ toolUseId: 'w1', name: 'create_po', input: { qty: 5 } });
    expect(draft(state).tools[0]).toMatchObject({
      id: 'w1',
      status: 'proposed',
      kind: 'write',
      input: { qty: 5 },
    });
  });

  it('resume re-announcing the confirmed write via tool_call does not duplicate the card or the replay block', () => {
    // Live-verification find: on approve, the backend re-announces the write with a tool_call for the id
    // the awaiting_confirmation upsert already placed in the SAME draft. Blind-appending rendered two
    // cards for one execution and, worse, made the commit cursor push a duplicate tool_use into the
    // replayed conversation (which the model API would then reject on the next turn).
    const parked = run([
      { type: 'user_send', id: 'm1', text: 'create PO' },
      sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
      sse({ type: 'done', stopReason: 'awaiting_confirmation' }),
    ]);
    const confirmed = assistantReducer(parked, {
      type: 'resolve_confirmation',
      approved: true,
      resultText: '',
    });
    const finished = run(
      [
        sse({ type: 'tool_call', id: 'w1', name: 'create_po', input: { qty: 5 }, kind: 'write' }),
        sse({ type: 'tool_result', id: 'w1', ok: true, result: '{"poId":42}' }),
        sse({ type: 'text_delta', text: 'Done.' }),
        sse({ type: 'done', stopReason: 'end_turn' }),
      ],
      confirmed,
    );
    // One card, settled.
    expect(draft(finished).tools).toHaveLength(1);
    expect(draft(finished).tools[0]).toMatchObject({ id: 'w1', status: 'success' });
    // Exactly one tool_use block for w1 in the replay history.
    const toolUses = finished.conversation.flatMap((m) =>
      m.content.filter((b) => b.type === 'tool_use' && b.id === 'w1'),
    );
    expect(toolUses).toHaveLength(1);
  });

  it('still parks correctly when a write tool_call did precede awaiting_confirmation (no duplicate card)', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'create PO' },
      sse({ type: 'tool_call', id: 'w1', name: 'create_po', input: { qty: 5 }, kind: 'write' }),
      sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
      sse({ type: 'done', stopReason: 'awaiting_confirmation' }),
    ]);
    expect(state.pending?.toolUseId).toBe('w1');
    expect(draft(state).tools).toHaveLength(1);
    expect(draft(state).tools[0]).toMatchObject({ status: 'proposed', kind: 'write' });
  });

  it('on confirm: clears pending, resumes streaming, keeps the tool running', () => {
    const parked = run([
      { type: 'user_send', id: 'm1', text: 'create PO' },
      sse({ type: 'text_delta', text: 'Creating…' }),
      sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
      sse({ type: 'done', stopReason: 'awaiting_confirmation' }),
    ]);
    const confirmed = assistantReducer(parked, {
      type: 'resolve_confirmation',
      approved: true,
      resultText: '',
    });
    expect(confirmed.pending).toBeNull();
    expect(confirmed.streaming).toBe(true);
    expect(draft(confirmed).tools[0]).toMatchObject({ status: 'running' });
    // The assistant turn (text + tool_use) is committed so the resume POST can replay it.
    expect(confirmed.conversation.at(-1)).toEqual({
      role: 'assistant',
      content: [
        { type: 'text', text: 'Creating…' },
        { type: 'tool_use', id: 'w1', name: 'create_po', input: { qty: 5 } },
      ],
    });
  });

  it('on reject: ends the turn, marks the tool error, commits a tool_result(isError) block', () => {
    const parked = run([
      { type: 'user_send', id: 'm1', text: 'create PO' },
      sse({ type: 'tool_call', id: 'w1', name: 'create_po', input: { qty: 5 }, kind: 'write' }),
      sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
      sse({ type: 'done', stopReason: 'awaiting_confirmation' }),
    ]);
    const rejected = assistantReducer(parked, {
      type: 'resolve_confirmation',
      approved: false,
      resultText: 'Rejected by user.',
    });
    expect(rejected.pending).toBeNull();
    expect(rejected.streaming).toBe(false);
    expect(draft(rejected).tools[0]).toMatchObject({ status: 'error', result: 'Rejected by user.' });
    // Assistant turn + user tool_result(isError) both committed.
    expect(rejected.conversation.at(-1)).toEqual({
      role: 'user',
      content: [{ type: 'tool_result', toolUseId: 'w1', content: 'Rejected by user.', isError: true }],
    });
  });

  it('surfaces an error event and stops streaming', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'hi' },
      sse({ type: 'error', title: 'Rate limited', detail: 'slow down', status: 429 }),
    ]);
    expect(state.streaming).toBe(false);
    expect(state.error).toEqual({ title: 'Rate limited', detail: 'slow down', status: 429 });
  });

  it('surfaces a transport failure via stream_failed', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'hi' },
      { type: 'stream_failed', title: 'Connection lost' },
    ]);
    expect(state.streaming).toBe(false);
    expect(state.error).toMatchObject({ title: 'Connection lost' });
  });

  it('reset returns to the initial empty state', () => {
    const state = run([
      { type: 'user_send', id: 'm1', text: 'hi' },
      sse({ type: 'text_delta', text: 'x' }),
      { type: 'reset' },
    ]);
    expect(state).toEqual(initialAssistantState);
  });

  it('clears a prior error when a new turn starts', () => {
    const withError = run([
      { type: 'user_send', id: 'm1', text: 'hi' },
      sse({ type: 'error', title: 'boom' }),
    ]);
    const next = assistantReducer(withError, { type: 'user_send', id: 'm2', text: 'again' });
    expect(next.error).toBeNull();
    expect(next.streaming).toBe(true);
  });

  describe('full HITL loop (read + write in one turn, resumed) commits a legal, non-duplicated conversation', () => {
    /** Runs the whole sequence: text + read tool_use/result, write tool_use, confirm, resumed write result +
     *  text, done(end_turn). Mirrors what the backend actually sends when a turn calls a read then a write. */
    function fullLoop() {
      const parked = run([
        { type: 'user_send', id: 'm1', text: 'order 5 widgets' },
        sse({ type: 'text_delta', text: 'Let me check that. ' }),
        sse({ type: 'tool_call', id: 'r1', name: 'list_items', input: { q: 'widget' }, kind: 'read' }),
        sse({ type: 'tool_result', id: 'r1', ok: true, result: '[{"sku":"W-1"}]' }),
        sse({ type: 'tool_call', id: 'w1', name: 'create_po', input: { qty: 5 }, kind: 'write' }),
        sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
        sse({ type: 'done', stopReason: 'awaiting_confirmation' }),
      ]);
      const confirmed = assistantReducer(parked, {
        type: 'resolve_confirmation',
        approved: true,
        resultText: '',
      });
      // Resumed stream: the write's result arrives, then closing text, then done(end_turn).
      const done = [
        sse({ type: 'tool_result', id: 'w1', ok: true, result: '{"poId":42}' }),
        sse({ type: 'text_delta', text: 'Purchase order created.' }),
        sse({ type: 'done', stopReason: 'end_turn' }),
      ].reduce(assistantReducer, confirmed);
      return { parked, confirmed, done };
    }

    it('does not duplicate the assistant tool_use turn after the resumed done', () => {
      const { confirmed, done } = fullLoop();
      // confirm already committed the assistant(text + tool_use(r1) + tool_use(w1)) + user(tool_result(r1))
      // turn; the resumed done must not push that same assistant turn again.
      const assistantTurns = done.conversation.filter((m) => m.role === 'assistant');
      expect(assistantTurns).toHaveLength(2);
      expect(confirmed.conversation.at(-2)).toEqual({
        role: 'assistant',
        content: [
          { type: 'text', text: 'Let me check that. ' },
          { type: 'tool_use', id: 'r1', name: 'list_items', input: { q: 'widget' } },
          { type: 'tool_use', id: 'w1', name: 'create_po', input: { qty: 5 } },
        ],
      });
    });

    it('carries the read tool_result in the confirm-time commit (available for the resume POST)', () => {
      const { confirmed } = fullLoop();
      expect(confirmed.conversation.at(-1)).toEqual({
        role: 'user',
        content: [{ type: 'tool_result', toolUseId: 'r1', content: '[{"sku":"W-1"}]', isError: false }],
      });
    });

    it('commits the write tool_result and trailing text after the resumed done, without re-pushing r1', () => {
      const { done } = fullLoop();
      // No tool_result(r1) block appears anywhere after the confirm-time commit.
      const r1ResultCount = done.conversation.filter((m) =>
        m.content.some((b) => b.type === 'tool_result' && b.toolUseId === 'r1'),
      ).length;
      expect(r1ResultCount).toBe(1);
      expect(done.conversation.at(-2)).toEqual({
        role: 'user',
        content: [{ type: 'tool_result', toolUseId: 'w1', content: '{"poId":42}', isError: false }],
      });
      expect(done.conversation.at(-1)).toEqual({
        role: 'assistant',
        content: [{ type: 'text', text: 'Purchase order created.' }],
      });
    });

    it('produces a legal, replayable sequence: every tool_use has exactly one matching tool_result, no duplicate blocks, and every tool_use is preceded by its tool_use before any tool_result references it', () => {
      const { done } = fullLoop();
      const toolUseIds = new Set<string>();
      const toolResultIds = new Set<string>();
      const toolUseSeenBefore = new Set<string>();
      for (const m of done.conversation) {
        for (const block of m.content) {
          if (block.type === 'tool_use') {
            expect(toolUseIds.has(block.id)).toBe(false); // no duplicate tool_use
            toolUseIds.add(block.id);
            toolUseSeenBefore.add(block.id);
          }
          if (block.type === 'tool_result') {
            expect(toolResultIds.has(block.toolUseId)).toBe(false); // no duplicate tool_result
            expect(toolUseSeenBefore.has(block.toolUseId)).toBe(true); // tool_use precedes its tool_result
            toolResultIds.add(block.toolUseId);
          }
        }
      }
      expect(toolResultIds).toEqual(toolUseIds);
      expect([...toolUseIds].sort()).toEqual(['r1', 'w1']);
      // Two consecutive `user` turns are expected here (the read's tool_result, committed at confirm time,
      // is immediately followed by the write's tool_result, committed after resume) — this mirrors the
      // backend's own resume behaviour (AgentLoopService.resumeFromDecision appends a fresh user message
      // rather than merging into an earlier one), which the Anthropic Messages API accepts even though it
      // isn't the "one block per turn" ideal. What must never happen is two consecutive `assistant` turns.
      for (let i = 1; i < done.conversation.length; i++) {
        const current = done.conversation[i];
        const previous = done.conversation[i - 1];
        if (current?.role === 'assistant') {
          expect(previous?.role).toBe('user');
        }
      }
    });
  });

  describe('double-submit protection', () => {
    it('a second resolve_confirmation after pending is cleared is a no-op', () => {
      const parked = run([
        { type: 'user_send', id: 'm1', text: 'create PO' },
        sse({ type: 'tool_call', id: 'w1', name: 'create_po', input: { qty: 5 }, kind: 'write' }),
        sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
        sse({ type: 'done', stopReason: 'awaiting_confirmation' }),
      ]);
      const first = assistantReducer(parked, {
        type: 'resolve_confirmation',
        approved: true,
        resultText: '',
      });
      // pending is now null; a second resolve (e.g. a duplicated dispatch) must not touch state further.
      const second = assistantReducer(first, {
        type: 'resolve_confirmation',
        approved: true,
        resultText: '',
      });
      expect(second).toBe(first);
    });
  });

  describe('EOF without a terminal event', () => {
    it('stream_failed after streaming started leaves a clean, non-streaming error state', () => {
      const state = run([
        { type: 'user_send', id: 'm1', text: 'hi' },
        sse({ type: 'text_delta', text: 'partial' }),
        { type: 'stream_failed', title: 'Connection lost' },
      ]);
      expect(state.streaming).toBe(false);
      expect(state.error).toMatchObject({ title: 'Connection lost' });
      // The partial draft text is preserved (not discarded) so the user still sees what streamed so far.
      expect(draft(state).text).toBe('partial');
    });
  });

  describe('a second awaiting_confirmation while one is already pending', () => {
    it('is treated as a protocol error rather than silently replacing the pending confirmation', () => {
      const parked = run([
        { type: 'user_send', id: 'm1', text: 'do two things' },
        sse({ type: 'tool_call', id: 'w1', name: 'create_po', input: { qty: 5 }, kind: 'write' }),
        sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
      ]);
      expect(parked.pending).toEqual({ toolUseId: 'w1', name: 'create_po', input: { qty: 5 } });

      const second = assistantReducer(
        parked,
        sse({ type: 'awaiting_confirmation', id: 'w2', name: 'create_so', input: {} }),
      );
      expect(second.streaming).toBe(false);
      expect(second.error).not.toBeNull();
      // The original pending confirmation is preserved rather than clobbered by the unexpected second one.
      expect(second.pending).toEqual({ toolUseId: 'w1', name: 'create_po', input: { qty: 5 } });
    });
  });

  describe('awaiting_confirmation pauses immediately, ahead of the done that follows', () => {
    it('sets streaming false as soon as awaiting_confirmation arrives, not only on done', () => {
      const afterAwaiting = run([
        { type: 'user_send', id: 'm1', text: 'create PO' },
        sse({ type: 'tool_call', id: 'w1', name: 'create_po', input: { qty: 5 }, kind: 'write' }),
        sse({ type: 'awaiting_confirmation', id: 'w1', name: 'create_po', input: { qty: 5 } }),
      ]);
      expect(afterAwaiting.streaming).toBe(false);
      expect(afterAwaiting.pending).not.toBeNull();
      // The done(awaiting_confirmation) that follows is a harmless no-op on top of this.
      const afterDone = assistantReducer(afterAwaiting, sse({ type: 'done', stopReason: 'awaiting_confirmation' }));
      expect(afterDone.streaming).toBe(false);
      expect(afterDone.pending).toEqual(afterAwaiting.pending);
    });
  });

  describe('Stop mid-flight', () => {
    it('cancels an unfinished tool with a synthesized error tool_result, keeping the sequence replayable', () => {
      const state = run([
        { type: 'user_send', id: 'm1', text: 'do something slow' },
        sse({ type: 'text_delta', text: 'Working on it…' }),
        sse({ type: 'tool_call', id: 't1', name: 'slow_read', input: {}, kind: 'read' }),
      ]);
      const stopped = assistantReducer(state, { type: 'stop', cancelledText: 'Cancelled by user.' });
      expect(stopped.streaming).toBe(false);
      expect(stopped.conversation).toEqual([
        { role: 'user', content: [{ type: 'text', text: 'do something slow' }] },
        {
          role: 'assistant',
          content: [
            { type: 'text', text: 'Working on it…' },
            { type: 'tool_use', id: 't1', name: 'slow_read', input: {} },
          ],
        },
        {
          role: 'user',
          content: [{ type: 'tool_result', toolUseId: 't1', content: 'Cancelled by user.', isError: true }],
        },
      ]);

      // Sending a new message afterwards still produces a legal, replayable sequence (no dangling tool_use).
      const next = assistantReducer(stopped, { type: 'user_send', id: 'm2', text: 'try again' });
      const toolUseIds = new Set<string>();
      const toolResultIds = new Set<string>();
      for (const m of next.conversation) {
        for (const b of m.content) {
          if (b.type === 'tool_use') toolUseIds.add(b.id);
          if (b.type === 'tool_result') toolResultIds.add(b.toolUseId);
        }
      }
      expect(toolResultIds).toEqual(toolUseIds);
    });

    it('does not synthesize a cancelled result for a tool that already finished before Stop', () => {
      const state = run([
        { type: 'user_send', id: 'm1', text: 'go' },
        sse({ type: 'tool_call', id: 't1', name: 'do', input: {}, kind: 'read' }),
        sse({ type: 'tool_result', id: 't1', ok: true, result: 'ok' }),
      ]);
      const stopped = assistantReducer(state, { type: 'stop', cancelledText: 'Cancelled by user.' });
      expect(stopped.conversation.at(-1)).toEqual({
        role: 'user',
        content: [{ type: 'tool_result', toolUseId: 't1', content: 'ok', isError: false }],
      });
    });
  });

  describe('preset', () => {
    it('has no preset by default', () => {
      expect(initialAssistantState.preset).toBeNull();
    });

    it('sets the preset from the first user_send', () => {
      const state = run([{ type: 'user_send', id: 'm1', text: 'why?', preset: 'reconciliation' }]);
      expect(state.preset).toBe('reconciliation');
    });

    it('a plain user_send (no preset) leaves the preset null', () => {
      const state = run([{ type: 'user_send', id: 'm1', text: 'hi' }]);
      expect(state.preset).toBeNull();
    });

    it('keeps the first preset for later messages in the same conversation, ignoring a later preset arg', () => {
      const state = run([
        { type: 'user_send', id: 'm1', text: 'why?', preset: 'reconciliation' },
        sse({ type: 'done', stopReason: 'end_turn' }),
        // A second send in the same conversation passing a different preset must not override it —
        // conversation.length is already > 0 by this point.
        { type: 'user_send', id: 'm2', text: 'and then?', preset: 'margin' },
      ]);
      expect(state.preset).toBe('reconciliation');
    });

    it('reset clears the preset back to null', () => {
      const state = run([
        { type: 'user_send', id: 'm1', text: 'why?', preset: 'reconciliation' },
        { type: 'reset' },
      ]);
      expect(state.preset).toBeNull();
    });
  });
});
