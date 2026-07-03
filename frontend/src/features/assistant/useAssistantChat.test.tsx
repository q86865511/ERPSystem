import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
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
