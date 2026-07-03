import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../i18n';
import type { SseAssistantEvent } from './types';
import { useAssistantChat } from './useAssistantChat';

// Mocks the SSE transport so tests drive `streamChat` directly instead of a real fetch/ReadableStream —
// this suite is about useAssistantChat's own stream-lifecycle handling (EOF without a terminal event),
// not the wire parsing (covered by api.test.ts) or the reducer (covered by assistantReducer.test.ts).
const { streamChatMock } = vi.hoisted(() => ({ streamChatMock: vi.fn() }));
vi.mock('./api', () => ({
  streamChat: streamChatMock,
  AssistantHttpError: class AssistantHttpError extends Error {},
}));

// Resets call history (and any one-off mockReturnValueOnce queue) between tests so an earlier test's
// recorded calls/queued returns can never leak into a later assertion on this shared mock.
beforeEach(() => {
  streamChatMock.mockReset();
});

/** An async generator yielding the given events then returning (a clean EOF, no throw). */
async function* eofAfter(events: SseAssistantEvent[]) {
  for (const e of events) yield e;
}

function wrapper({ children }: { children: ReactNode }) {
  return <I18nProvider>{children}</I18nProvider>;
}

describe('useAssistantChat — stream lifecycle', () => {
  it('dispatches stream_failed when the stream ends (EOF) without a terminal done/error event', async () => {
    streamChatMock.mockReturnValue(eofAfter([{ type: 'text_delta', text: 'partial' }]));
    const { result } = renderHook(() => useAssistantChat(), { wrapper });

    act(() => result.current.send('hello'));

    await waitFor(() => expect(result.current.streaming).toBe(false));
    expect(result.current.error).not.toBeNull();
    // The partial text that streamed before the drop is preserved, not discarded.
    expect(result.current.messages.at(-1)).toMatchObject({ kind: 'assistant', text: 'partial' });
  });

  it('does not report stream_failed when the stream ends normally after a done event', async () => {
    streamChatMock.mockReturnValue(eofAfter([{ type: 'done', stopReason: 'end_turn' }]));
    const { result } = renderHook(() => useAssistantChat(), { wrapper });

    act(() => result.current.send('hello'));

    await waitFor(() => expect(result.current.streaming).toBe(false));
    expect(result.current.error).toBeNull();
  });
});

describe('useAssistantChat — preset', () => {
  it('sends the preset on the first message', async () => {
    streamChatMock.mockReturnValue(eofAfter([{ type: 'done', stopReason: 'end_turn' }]));
    const { result } = renderHook(() => useAssistantChat(), { wrapper });

    act(() => result.current.send('why?', 'reconciliation'));
    await waitFor(() => expect(result.current.streaming).toBe(false));

    expect(streamChatMock).toHaveBeenCalledWith(
      expect.objectContaining({ preset: 'reconciliation' }),
      expect.anything(),
    );
  });

  it('omits preset when the conversation was not started from a preset chip', async () => {
    streamChatMock.mockReturnValue(eofAfter([{ type: 'done', stopReason: 'end_turn' }]));
    const { result } = renderHook(() => useAssistantChat(), { wrapper });

    act(() => result.current.send('hello'));
    await waitFor(() => expect(result.current.streaming).toBe(false));

    expect(streamChatMock).toHaveBeenCalledTimes(1);
    expect(streamChatMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ preset: undefined }),
      expect.anything(),
    );
  });

  it('resends the same preset on a write-confirmation resume', async () => {
    streamChatMock
      .mockReturnValueOnce(
        eofAfter([
          { type: 'tool_call', id: 'w1', name: 'create_sales_order', input: {}, kind: 'write' },
          { type: 'awaiting_confirmation', id: 'w1', name: 'create_sales_order', input: {} },
          { type: 'done', stopReason: 'awaiting_confirmation' },
        ]),
      )
      .mockReturnValueOnce(eofAfter([{ type: 'done', stopReason: 'end_turn' }]));
    const { result } = renderHook(() => useAssistantChat(), { wrapper });

    act(() => result.current.send('make an order', 'margin'));
    await waitFor(() => expect(result.current.pending).not.toBeNull());

    act(() => result.current.resolveConfirmation(true));
    await waitFor(() => expect(result.current.streaming).toBe(false));

    expect(streamChatMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ preset: 'margin' }),
      expect.anything(),
    );
  });
});
