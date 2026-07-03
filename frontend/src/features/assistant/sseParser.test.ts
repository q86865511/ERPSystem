import { describe, expect, it } from 'vitest';
import { parseSseStream, type SseEvent } from './sseParser';

/** Builds a ReadableStream that emits the given string chunks (as UTF-8 bytes) in order. */
function streamOf(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  let i = 0;
  return new ReadableStream({
    pull(controller) {
      if (i < chunks.length) {
        controller.enqueue(encoder.encode(chunks[i++]));
      } else {
        controller.close();
      }
    },
  });
}

async function collect(chunks: string[]): Promise<SseEvent[]> {
  const out: SseEvent[] = [];
  for await (const ev of parseSseStream(streamOf(chunks))) out.push(ev);
  return out;
}

describe('parseSseStream', () => {
  it('parses a single event with event name and data', async () => {
    const events = await collect(['event: text_delta\ndata: {"text":"hi"}\n\n']);
    expect(events).toEqual([{ event: 'text_delta', data: '{"text":"hi"}' }]);
  });

  it('reassembles a frame split across chunk boundaries', async () => {
    const events = await collect(['event: tool_ca', 'll\ndata: {"id":"t1"', '}\n\n']);
    expect(events).toEqual([{ event: 'tool_call', data: '{"id":"t1"}' }]);
  });

  it('joins multi-line data with newlines', async () => {
    const events = await collect(['data: line1\ndata: line2\n\n']);
    expect(events).toEqual([{ event: 'message', data: 'line1\nline2' }]);
  });

  it('handles CRLF line endings', async () => {
    const events = await collect(['event: done\r\ndata: {}\r\n\r\n']);
    expect(events).toEqual([{ event: 'done', data: '{}' }]);
  });

  it('parses multiple events in one chunk', async () => {
    const events = await collect([
      'event: text_delta\ndata: a\n\nevent: text_delta\ndata: b\n\n',
    ]);
    expect(events).toEqual([
      { event: 'text_delta', data: 'a' },
      { event: 'text_delta', data: 'b' },
    ]);
  });

  it('ignores comment (heartbeat) lines', async () => {
    const events = await collect([': keep-alive\n\nevent: done\ndata: {}\n\n']);
    expect(events).toEqual([{ event: 'done', data: '{}' }]);
  });

  it('flushes a trailing frame that lacks a terminating blank line', async () => {
    const events = await collect(['event: done\ndata: {}']);
    expect(events).toEqual([{ event: 'done', data: '{}' }]);
  });

  it('strips only a single leading space after the colon', async () => {
    const events = await collect(['data:  two-leading-spaces\n\n']);
    expect(events).toEqual([{ event: 'message', data: ' two-leading-spaces' }]);
  });
});
