/**
 * Minimal, zero-dependency Server-Sent Events parser over a fetch `ReadableStream`. The backend streams the
 * chat turn as SSE frames (`event:` + one or more `data:` lines, terminated by a blank line); openapi-fetch
 * can't consume a stream, so we read the raw body here.
 *
 * Handles the wire realities the spec calls out: frames split across chunk boundaries, multi-line `data:`
 * (joined with `\n` per the SSE spec), and CRLF or LF line endings. Comment lines (`:` prefix, used for
 * heartbeats) and unknown fields are ignored. `data` defaults to the empty string when a frame carries only
 * an event name.
 */
export interface SseEvent {
  /** The `event:` field, or `'message'` when omitted (per the SSE default). */
  event: string;
  /** The joined `data:` payload (multiple data lines joined with `\n`). */
  data: string;
}

/** Splits accumulated text into complete frames on the blank-line delimiter, tolerating CRLF and bare CR. */
function splitFrames(buffer: string): { frames: string[]; rest: string } {
  // Normalise CRLF/CR to LF first so the delimiter check is uniform.
  const normalised = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const parts = normalised.split('\n\n');
  // The last element is a possibly-incomplete frame (no terminating blank line yet) — keep it buffered.
  const rest = parts.pop() ?? '';
  return { frames: parts, rest };
}

/** Parses one already-delimited frame block into an SseEvent, or null if it carries no data/event. */
function parseFrame(frame: string): SseEvent | null {
  let event = 'message';
  const dataLines: string[] = [];
  let sawField = false;
  for (const raw of frame.split('\n')) {
    if (raw === '' || raw.startsWith(':')) continue; // blank or comment line
    const colon = raw.indexOf(':');
    const field = colon === -1 ? raw : raw.slice(0, colon);
    // Per spec, a single leading space after the colon is stripped.
    let value = colon === -1 ? '' : raw.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') {
      event = value;
      sawField = true;
    } else if (field === 'data') {
      dataLines.push(value);
      sawField = true;
    }
    // id/retry/unknown fields are ignored.
  }
  if (!sawField) return null;
  return { event, data: dataLines.join('\n') };
}

/**
 * Consumes a fetch response body stream and yields parsed SSE events as they arrive. The caller drives it
 * with `for await`; passing the request's AbortSignal to fetch is enough to stop iteration.
 */
export async function* parseSseStream(
  stream: ReadableStream<Uint8Array>,
): AsyncGenerator<SseEvent, void, unknown> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const { frames, rest } = splitFrames(buffer);
      buffer = rest;
      for (const frame of frames) {
        const parsed = parseFrame(frame);
        if (parsed) yield parsed;
      }
    }
    // Flush any trailing frame that lacked a terminating blank line at stream end.
    buffer += decoder.decode();
    const trailing = parseFrame(buffer);
    if (trailing) yield trailing;
  } finally {
    reader.releaseLock();
  }
}
