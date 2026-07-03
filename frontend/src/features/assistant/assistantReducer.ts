import type {
  ContentBlock,
  Message,
  PendingConfirmation,
  SseAssistantEvent,
  UiMessage,
  UiToolCall,
} from './types';

/**
 * Pure reducer for the assistant conversation. It owns two views (see types.ts): `conversation` (raw blocks
 * sent back to the stateless backend) and `messages` (the render model). Every SSE event and user action is
 * an action here, so the whole state machine is unit-testable without React, fetch or timers.
 *
 * Streaming model: a turn opens an empty assistant UI message (the "draft"); text_delta appends to its text,
 * tool_call adds a tool card, tool_result resolves it. `commitDraft` (below) pushes the draft's wire blocks
 * into `conversation` and is idempotent via a commit cursor stamped onto the draft, because a HITL turn
 * commits in two steps rather than one:
 *  1. `resolve_confirmation` commits the assistant turn (text + tool_use, including the write just
 *     confirmed/rejected) — plus, on reject, its tool_result — immediately, so the resume POST can carry it.
 *  2. the resumed turn's `done(end_turn)` commits the rest: the write's own tool_result (which only exists
 *     once the backend has executed it) and any text streamed after resuming.
 * A plain (non-HITL) turn only ever hits step 2, committing everything in one shot — same code path, cursor
 * just starts and ends in one call. See `commitDraft`'s doc for exactly what each call contributes.
 */
export interface AssistantState {
  /** Raw block history POSTed to the backend (source of truth for replay). */
  conversation: Message[];
  /** Render model painted by the Drawer. */
  messages: UiMessage[];
  /** True while an SSE stream is open (input disabled, stop button shown). */
  streaming: boolean;
  /** Set when a write tool is awaiting the user's confirm/reject; blocks further input. */
  pending: PendingConfirmation | null;
  /** Last stream error surfaced to the user (banner), cleared when a new turn starts. */
  error: { title: string; detail?: string; status?: number } | null;
}

export const initialAssistantState: AssistantState = {
  conversation: [],
  messages: [],
  streaming: false,
  pending: null,
  error: null,
};

export type AssistantAction =
  /** User pressed send: append their text and open a streaming assistant turn. */
  | { type: 'user_send'; id: string; text: string }
  /** User confirmed/rejected the pending write tool (drives the resume POST at the hook layer). */
  | { type: 'resolve_confirmation'; approved: boolean; resultText: string }
  /** A parsed SSE event from the open stream. */
  | { type: 'sse'; event: SseAssistantEvent }
  /** The stream ended abnormally (network/abort, or a malformed known SSE frame) without a `done` event. */
  | { type: 'stream_failed'; title: string; detail?: string; status?: number }
  /** User pressed Stop: abort whatever was in flight; `cancelledText` settles any unfinished tool card. */
  | { type: 'stop'; cancelledText: string }
  /** User cleared the conversation. */
  | { type: 'reset' };

/** Maps over the current assistant draft message, leaving earlier messages untouched. */
function mapDraft(
  messages: UiMessage[],
  fn: (draft: Extract<UiMessage, { kind: 'assistant' }>) => UiMessage,
): UiMessage[] {
  return messages.map((m, i) => (i === messages.length - 1 && m.kind === 'assistant' ? fn(m) : m));
}

function updateTool(tools: UiToolCall[], id: string, patch: Partial<UiToolCall>): UiToolCall[] {
  return tools.map((t) => (t.id === id ? { ...t, ...patch } : t));
}

export type AssistantDraft = Extract<UiMessage, { kind: 'assistant' }>;

/**
 * Commits whatever part of the draft hasn't been committed yet into `conversation`, and returns the updated
 * conversation plus the advanced commit cursor (to be stamped back onto the draft by the caller).
 *
 * Idempotent by design: a HITL turn calls this once on confirm (to commit the assistant text/tool_use so the
 * resume POST can carry it) and again on the resumed `done` (to pick up the tool_result(s) that arrived
 * meanwhile, plus any later text) — the cursor on the draft ensures the second call never re-pushes blocks
 * the first call already committed.
 *
 * A tool with no terminal result yet (still `running`/`proposed` — e.g. Stop was pressed mid-flight) gets a
 * synthesized `tool_result(isError: true, "Cancelled by user.")` so every committed tool_use still has a
 * matching tool_result and `conversation` stays a replayable Anthropic-shaped sequence.
 */
export function commitDraft(
  conversation: Message[],
  draft: AssistantDraft,
): { conversation: Message[]; committed: NonNullable<AssistantDraft['committed']> } {
  const already = draft.committed ?? { textLen: 0, toolUseCount: 0, resultIds: [] };
  // `draft.text` only ever grows (text_delta appends), so the uncommitted tail is whatever's past textLen —
  // this is what lets a resumed turn's later text (streamed after confirm) get its own commit instead of
  // being silently dropped because the leading text was "already emitted".
  const newText = draft.text.slice(already.textLen);
  const newTools = draft.tools.slice(already.toolUseCount);
  const resultIds = new Set(already.resultIds);

  // A tool_result for a tool whose tool_use was committed in an *earlier* call (e.g. the write, confirmed
  // last time) reflects something that finished before this commit's new text/tool_use — chronologically
  // (and role-wise) it belongs in a user turn ahead of them. A tool_result for a tool whose tool_use is only
  // being committed *this* call (e.g. a read resolved before the pause) must instead follow it, since the
  // tool_use has to appear before its own result. Splitting on that boundary keeps ordering correct for both
  // the confirm-time commit (new tool_use + its own already-resolved result) and the post-resume commit
  // (an earlier tool_use's just-arrived result, followed by new text).
  const priorResults: ContentBlock[] = [];
  const ownResults: ContentBlock[] = [];
  for (const tool of draft.tools) {
    if (resultIds.has(tool.id)) continue;
    if (tool.status !== 'success' && tool.status !== 'error') continue;
    const block: ContentBlock = {
      type: 'tool_result',
      toolUseId: tool.id,
      content: tool.result ?? '',
      isError: tool.status === 'error',
    };
    const isNewToolUse = draft.tools.indexOf(tool) >= already.toolUseCount;
    (isNewToolUse ? ownResults : priorResults).push(block);
    resultIds.add(tool.id);
  }

  const assistantBlocks: ContentBlock[] = [];
  if (newText) assistantBlocks.push({ type: 'text', text: newText });
  for (const tool of newTools) {
    assistantBlocks.push({ type: 'tool_use', id: tool.id, name: tool.name, input: tool.input });
  }

  const next = [...conversation];
  if (priorResults.length > 0) next.push({ role: 'user', content: priorResults });
  if (assistantBlocks.length > 0) next.push({ role: 'assistant', content: assistantBlocks });
  if (ownResults.length > 0) next.push({ role: 'user', content: ownResults });

  return {
    conversation: next,
    committed: { textLen: draft.text.length, toolUseCount: draft.tools.length, resultIds: [...resultIds] },
  };
}

/**
 * Like `commitDraft`, but any tool left without a terminal result (running/proposed) is treated as
 * cancelled: a synthesized `tool_result(isError: true, "Cancelled by user.")` is committed for it instead of
 * being left dangling. Used when the user presses Stop mid-flight, so the next turn's history stays a legal,
 * replayable sequence (every committed tool_use has exactly one matching tool_result).
 */
function commitDraftCancelling(
  conversation: Message[],
  draft: AssistantDraft,
  cancelledText: string,
): { conversation: Message[]; committed: NonNullable<AssistantDraft['committed']> } {
  const settled: AssistantDraft = {
    ...draft,
    tools: draft.tools.map((tool) =>
      tool.status === 'running' || tool.status === 'proposed'
        ? { ...tool, status: 'error' as const, result: cancelledText }
        : tool,
    ),
  };
  return commitDraft(conversation, settled);
}

/** Stamps a draft's `committed` cursor after a `commitDraft`/`commitDraftCancelling` call. */
function stampCommitted(
  messages: UiMessage[],
  committed: NonNullable<AssistantDraft['committed']>,
): UiMessage[] {
  return mapDraft(messages, (d) => ({ ...d, committed }));
}

export function assistantReducer(state: AssistantState, action: AssistantAction): AssistantState {
  switch (action.type) {
    case 'reset':
      return initialAssistantState;

    case 'user_send': {
      const userBlock: Message = {
        role: 'user',
        content: [{ type: 'text', text: action.text }],
      };
      return {
        ...state,
        error: null,
        streaming: true,
        pending: null,
        conversation: [...state.conversation, userBlock],
        messages: [
          ...state.messages,
          { kind: 'user', id: action.id, text: action.text },
          { kind: 'assistant', id: `${action.id}-a`, text: '', tools: [] },
        ],
      };
    }

    case 'stream_failed':
      return {
        ...state,
        streaming: false,
        error: { title: action.title, detail: action.detail, status: action.status },
      };

    case 'stop': {
      // Cancel whatever tool(s) were still in flight so the committed history stays a legal, replayable
      // sequence (every tool_use gets a matching tool_result) even though the model never finished the turn.
      const draft = state.messages.at(-1);
      if (!draft || draft.kind !== 'assistant') {
        return { ...state, streaming: false };
      }
      const { conversation, committed } = commitDraftCancelling(
        state.conversation,
        draft,
        action.cancelledText,
      );
      return {
        ...state,
        streaming: false,
        pending: null,
        conversation,
        messages: stampCommitted(state.messages, committed),
      };
    }

    case 'resolve_confirmation': {
      if (!state.pending) return state; // no pending confirmation (or a duplicate resolve) — no-op
      const { toolUseId, approved } = { toolUseId: state.pending.toolUseId, approved: action.approved };

      // Resolve the proposed tool card: approval keeps it running (its own tool_result arrives once the
      // resumed stream executes it); rejection settles it to error immediately.
      const messages = mapDraft(state.messages, (draft) => ({
        ...draft,
        tools: updateTool(draft.tools, toolUseId, {
          status: approved ? 'running' : 'error',
          result: approved ? undefined : action.resultText,
        }),
      }));
      const draft = messages.at(-1) as AssistantDraft;

      // Commit now regardless of approve/reject: this pushes the assistant turn (text + tool_use, including
      // the just-resolved write) — and, for reject, the tool_result(isError) right along with it — into
      // `conversation`. On approve the write's own tool_result isn't committed yet (it doesn't exist until
      // the resumed stream runs it); the commit cursor stamped onto the draft means the later `done` commit
      // won't re-push what's already here, only the new tool_result plus anything after it.
      const { conversation, committed } = commitDraft(state.conversation, draft);

      return {
        ...state,
        conversation,
        messages: stampCommitted(messages, committed),
        pending: null,
        streaming: approved, // approve resumes the stream; reject ends the turn locally
      };
    }

    case 'sse':
      return applySse(state, action.event);

    default:
      return state;
  }
}

function applySse(state: AssistantState, event: SseAssistantEvent): AssistantState {
  switch (event.type) {
    case 'text_delta':
      return {
        ...state,
        messages: mapDraft(state.messages, (d) => ({ ...d, text: d.text + event.text })),
      };

    case 'tool_call':
      return {
        ...state,
        messages: mapDraft(state.messages, (d) => ({
          ...d,
          tools: [
            ...d.tools,
            {
              id: event.id,
              name: event.name,
              kind: event.kind,
              input: event.input,
              // Reads start running immediately; writes surface as a proposal for confirmation.
              status: event.kind === 'write' ? 'proposed' : 'running',
            },
          ],
        })),
      };

    case 'tool_result':
      return {
        ...state,
        messages: mapDraft(state.messages, (d) => ({
          ...d,
          tools: updateTool(d.tools, event.id, {
            status: event.ok ? 'success' : 'error',
            result: event.result,
          }),
        })),
      };

    case 'awaiting_confirmation':
      // The backend guarantees a single stream carries at most one awaiting_confirmation (it stops the
      // stream at the first write tool it meets). A second one while `pending` is still set would mean the
      // backend broke that guarantee (or a frame got misrouted) — treat it as a protocol error rather than
      // silently clobbering the confirmation the user hasn't answered yet.
      if (state.pending) {
        return {
          ...state,
          streaming: false,
          error: { title: 'Unexpected second confirmation request from the assistant.' },
        };
      }
      // Pause immediately: don't wait for the `done(awaiting_confirmation)` that follows to unblock input.
      return {
        ...state,
        streaming: false,
        pending: { toolUseId: event.id, name: event.name, input: event.input },
      };

    case 'done':
      // A stream that paused for confirmation already set `pending` and `streaming: false` on the
      // `awaiting_confirmation` event above; this is a no-op confirmation of the same state.
      if (event.stopReason === 'awaiting_confirmation') {
        return { ...state, streaming: false };
      }
      if (event.stopReason === 'error') {
        // No further detail is carried on `done(error)` itself (a separate `error` SSE event, handled
        // below, carries title/detail when the backend sends one) — leave title unset so the Drawer falls
        // back to its generic error copy instead of a meaningless literal.
        return {
          ...state,
          streaming: false,
          error: { title: '' },
        };
      }
      // end_turn / max_turns: commit whatever part of the streamed draft hasn't been committed yet (all of
      // it on a plain turn; just the tail — new tool_result(s) and any later text — on a resumed HITL turn
      // whose confirm step already committed the assistant text/tool_use).
      {
        const draft = state.messages.at(-1);
        if (!draft || draft.kind !== 'assistant') {
          return { ...state, streaming: false };
        }
        const { conversation, committed } = commitDraft(state.conversation, draft);
        return {
          ...state,
          streaming: false,
          conversation,
          messages: stampCommitted(state.messages, committed),
        };
      }

    case 'error':
      return {
        ...state,
        streaming: false,
        error: { title: event.title, detail: event.detail, status: event.status },
      };

    default:
      return state;
  }
}
