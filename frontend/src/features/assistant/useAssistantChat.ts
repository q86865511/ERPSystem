import { useCallback, useEffect, useReducer, useRef } from 'react';
import { useI18n } from '../../i18n';
import { AssistantHttpError, streamChat, type ChatBody } from './api';
import { assistantReducer, commitDraft, initialAssistantState, type AssistantDraft } from './assistantReducer';
import type { Decision, Message, UiToolCall } from './types';

/**
 * Orchestrates the assistant chat: owns the reducer state, drives the SSE stream, and handles the HITL
 * resume. The reducer stays pure (see assistantReducer.ts); this hook is the impure shell that runs fetch and
 * dispatches parsed events. `conversation` in state is always the exact block history the backend replays.
 */
export function useAssistantChat() {
  const { t } = useI18n();
  const [state, dispatch] = useReducer(assistantReducer, initialAssistantState);
  const abortRef = useRef<AbortController | null>(null);
  // Synchronous in-flight guard for resolveConfirmation: closure `state.pending` doesn't update until the
  // next render, so a second click fired before that render lands would still see the same non-null
  // `pending` and double-submit (double dispatch, double resume POST). Tracking the toolUseId currently
  // being resolved (rather than a plain boolean) means the guard clears itself as soon as a *different*
  // pending confirmation shows up, with no separate reset needed.
  const resolvingToolUseIdRef = useRef<string | null>(null);

  // Consumes a stream to completion, dispatching each event. Rejections/aborts become a stream_failed action.
  // Also guards against a "zombie" stream: if the underlying fetch stream ends (EOF) without ever yielding a
  // terminal `done`/`error` event, that's a dropped connection the SSE layer didn't surface as a rejection —
  // dispatch stream_failed ourselves so `streaming` doesn't stay stuck true forever.
  const runStream = useCallback(
    async (body: ChatBody) => {
      const controller = new AbortController();
      abortRef.current = controller;
      let sawTerminal = false;
      try {
        for await (const event of streamChat(body, controller.signal)) {
          if (event.type === 'done' || event.type === 'error') sawTerminal = true;
          dispatch({ type: 'sse', event });
        }
        if (!sawTerminal) {
          dispatch({ type: 'stream_failed', title: t('assistant.error.network') });
        }
      } catch (err) {
        if (controller.signal.aborted) return; // user pressed stop — not an error
        if (err instanceof AssistantHttpError) {
          dispatch({
            type: 'stream_failed',
            title: err.title,
            detail: err.detail,
            status: err.status,
          });
        } else {
          // Network failure or AssistantProtocolError (a malformed known-event frame) both surface as the
          // same generic "connection lost" banner — the user can't act differently on either.
          dispatch({ type: 'stream_failed', title: t('assistant.error.network') });
        }
      } finally {
        if (abortRef.current === controller) abortRef.current = null;
      }
    },
    [t],
  );

  // Abort any in-flight stream when the component unmounts (e.g. the drawer's chunk is torn down) so the
  // fetch doesn't keep running — and, more importantly, so it never dispatches into an unmounted reducer.
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const send = useCallback(
    (text: string, preset?: string) => {
      const trimmed = text.trim();
      if (!trimmed || state.streaming || state.pending) return;
      const id = `m${Date.now()}`;
      dispatch({ type: 'user_send', id, text: trimmed, preset });
      const messages: Message[] = [
        ...state.conversation,
        { role: 'user', content: [{ type: 'text', text: trimmed }] },
      ];
      // `preset` only takes effect on the first message (see the reducer); on a later message in the same
      // conversation, state.preset (already settled) is what must be resent.
      const effectivePreset = state.conversation.length === 0 ? preset : (state.preset ?? undefined);
      void runStream({ messages, preset: effectivePreset });
    },
    [state.conversation, state.streaming, state.pending, state.preset, runStream],
  );

  // Confirm/reject a pending write tool. On confirm we resume the stream with the full history + decision
  // (including any read tool_result already completed this turn); on reject we stop locally (the reducer
  // commits a tool_result(isError) block for the rejected write).
  const resolveConfirmation = useCallback(
    (approved: boolean) => {
      const pending = state.pending;
      // Guards a race the reducer's `if (!state.pending) return state` alone can't cover: a second click
      // firing before React re-renders with `pending: null` would still see the same stale, non-null
      // `pending` in this closure and POST a duplicate resume request even though the reducer itself no-ops
      // on the second dispatch. The ref is synchronous, so it catches the second call regardless of timing.
      if (!pending || resolvingToolUseIdRef.current === pending.toolUseId) return;
      resolvingToolUseIdRef.current = pending.toolUseId;
      const resultText = approved ? '' : t('assistant.tool.rejectedResult');
      const decision: Decision = { toolUseId: pending.toolUseId, approved };

      // Compute the exact same commit the reducer is about to perform (settle the resolved tool, then
      // commitDraft) so the resume POST body matches `conversation` after the dispatch below — this is what
      // carries this turn's already-completed read tool_result(s) alongside the write's tool_use, without
      // duplicating anything a later `done` will commit.
      const draft = state.messages.at(-1);
      let messages: Message[] = state.conversation;
      if (draft && draft.kind === 'assistant') {
        const settledTools: UiToolCall[] = draft.tools.map((tool) =>
          tool.id === pending.toolUseId
            ? { ...tool, status: approved ? 'running' : 'error', result: approved ? undefined : resultText }
            : tool,
        );
        const settledDraft: AssistantDraft = { ...draft, tools: settledTools };
        messages = commitDraft(state.conversation, settledDraft).conversation;
      }

      dispatch({ type: 'resolve_confirmation', approved, resultText });
      if (!approved) return; // rejecting ends the turn locally — nothing to resume
      void runStream({ messages, decision, preset: state.preset ?? undefined });
    },
    [state.pending, state.conversation, state.messages, state.preset, runStream, t],
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    // Whatever streamed so far stays on screen; the reducer commits it (cancelling any unfinished tool) so
    // the next turn's history is still a legal, replayable sequence.
    dispatch({ type: 'stop', cancelledText: t('assistant.tool.cancelledResult') });
  }, [t]);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    dispatch({ type: 'reset' });
  }, []);

  return {
    messages: state.messages,
    streaming: state.streaming,
    pending: state.pending,
    error: state.error,
    send,
    resolveConfirmation,
    stop,
    reset,
  };
}
