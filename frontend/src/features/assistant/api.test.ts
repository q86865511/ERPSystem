import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssistantProtocolError, streamChat } from './api';

/** Builds a Response streaming the given raw SSE body as a single chunk. */
function sseResponse(body: string): Response {
  return new Response(body, {
    status: 200,
    headers: { 'content-type': 'text/event-stream' },
  });
}

async function collect(res: Response): Promise<unknown[]> {
  const mockFetch = vi.fn(async () => res);
  vi.stubGlobal('fetch', mockFetch);
  const out: unknown[] = [];
  for await (const event of streamChat({ messages: [] }, new AbortController().signal)) {
    out.push(event);
  }
  return out;
}

describe('streamChat', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('yields typed events for a well-formed stream', async () => {
    const events = await collect(
      sseResponse('event: text_delta\ndata: {"text":"hi"}\n\nevent: done\ndata: {"stopReason":"end_turn"}\n\n'),
    );
    expect(events).toEqual([
      { type: 'text_delta', text: 'hi' },
      { type: 'done', stopReason: 'end_turn', usage: undefined },
    ]);
  });

  it('ignores an unknown event name regardless of its payload', async () => {
    const events = await collect(
      sseResponse('event: future_event\ndata: not json at all\n\nevent: done\ndata: {"stopReason":"end_turn"}\n\n'),
    );
    expect(events).toEqual([{ type: 'done', stopReason: 'end_turn', usage: undefined }]);
  });

  it('throws AssistantProtocolError for a known event with a non-JSON data payload', async () => {
    const mockFetch = vi.fn(async () => sseResponse('event: tool_call\ndata: {not json\n\n'));
    vi.stubGlobal('fetch', mockFetch);
    async function drain() {
      for await (const _ of streamChat({ messages: [] }, new AbortController().signal)) {
        // draining until the malformed frame throws
      }
    }
    await expect(drain()).rejects.toBeInstanceOf(AssistantProtocolError);
  });

  it('stops before a malformed frame but still yields events that arrived earlier', async () => {
    const mockFetch = vi.fn(async () =>
      sseResponse('event: text_delta\ndata: {"text":"partial"}\n\nevent: done\ndata: {not json\n\n'),
    );
    vi.stubGlobal('fetch', mockFetch);
    const seen: unknown[] = [];
    await expect(
      (async () => {
        for await (const event of streamChat({ messages: [] }, new AbortController().signal)) {
          seen.push(event);
        }
      })(),
    ).rejects.toBeInstanceOf(AssistantProtocolError);
    expect(seen).toEqual([{ type: 'text_delta', text: 'partial' }]);
  });
});
