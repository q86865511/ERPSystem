import { useQuery } from '@tanstack/react-query';
import { api, refreshAccessToken } from '../../api/client';
import { getAccessToken, toBearerHeader } from '../../auth/credentials';
import { parseSseStream } from './sseParser';
import type { Decision, Message, SseAssistantEvent } from './types';

/** Feature-flag status. Cheap, cached; the header entry only renders when `enabled`. */
export function useAssistantStatus() {
  return useQuery({
    queryKey: ['assistant', 'status'],
    queryFn: async () => {
      const { data, error } = await api.GET('/api/assistant/status');
      if (error) throw error;
      return data;
    },
    staleTime: 5 * 60_000,
  });
}

export interface ChatBody {
  messages: Message[];
  decision?: Decision;
  /** Optional analysis preset ('reconciliation' | 'margin') selecting a specialised system prompt. */
  preset?: string;
}

/** The six event names the backend contract defines; anything else is an unknown/future event (ignored). */
const KNOWN_EVENTS = new Set([
  'text_delta',
  'tool_call',
  'tool_result',
  'awaiting_confirmation',
  'done',
  'error',
]);

/**
 * Thrown when a frame with a known event name (one of the six the backend defines) carries a `data` payload
 * that doesn't parse as JSON — a protocol violation, as opposed to an unrecognised event name (which is
 * silently ignored, e.g. future event types or SSE comments/heartbeats misrouted through this path).
 */
export class AssistantProtocolError extends Error {
  constructor(eventName: string) {
    super(`Malformed SSE frame: event "${eventName}" had a non-JSON data payload`);
    this.name = 'AssistantProtocolError';
  }
}

/** Maps a raw SSE frame (event name + JSON data) to a typed assistant event, or null if unrecognised. */
function toAssistantEvent(name: string, data: string): SseAssistantEvent | null {
  const knownEvent = KNOWN_EVENTS.has(name);
  let payload: Record<string, unknown> = {};
  try {
    payload = data ? (JSON.parse(data) as Record<string, unknown>) : {};
  } catch {
    // A known event name with unparsable data is a protocol error (the caller must not silently drop it);
    // an unknown event name is ignored regardless of its payload, same as the `default` case below.
    if (knownEvent) throw new AssistantProtocolError(name);
    return null;
  }
  switch (name) {
    case 'text_delta':
      return { type: 'text_delta', text: String(payload.text ?? '') };
    case 'tool_call':
      return {
        type: 'tool_call',
        id: String(payload.id),
        name: String(payload.name),
        input: payload.input,
        kind: payload.kind === 'write' ? 'write' : 'read',
      };
    case 'tool_result':
      return {
        type: 'tool_result',
        id: String(payload.id),
        ok: Boolean(payload.ok),
        result: typeof payload.result === 'string' ? payload.result : JSON.stringify(payload.result ?? ''),
      };
    case 'awaiting_confirmation':
      return {
        type: 'awaiting_confirmation',
        id: String(payload.id),
        name: String(payload.name),
        input: payload.input,
      };
    case 'done':
      return {
        type: 'done',
        stopReason: (payload.stopReason as SseAssistantEvent extends { stopReason: infer R } ? R : never) ??
          'end_turn',
        usage: payload.usage as { inputTokens: number; outputTokens: number } | undefined,
      };
    case 'error':
      return {
        type: 'error',
        title: String(payload.title ?? 'error'),
        detail: payload.detail ? String(payload.detail) : undefined,
        status: typeof payload.status === 'number' ? payload.status : undefined,
      };
    default:
      return null;
  }
}

/** Thrown for a non-SSE error response (RFC 9457 problem+json) so the caller can surface title/detail/status. */
export class AssistantHttpError extends Error {
  constructor(
    readonly title: string,
    readonly detail: string | undefined,
    readonly status: number,
  ) {
    super(title);
    this.name = 'AssistantHttpError';
  }
}

async function problemToError(res: Response): Promise<AssistantHttpError> {
  let title = 'error';
  let detail: string | undefined;
  try {
    const p = (await res.json()) as { title?: string; detail?: string };
    title = p.title ?? title;
    detail = p.detail;
  } catch {
    // Non-JSON body; keep the generic title.
  }
  return new AssistantHttpError(title, detail, res.status);
}

/**
 * POSTs a chat turn and yields parsed SSE events. Uses raw fetch (openapi-fetch can't stream), so it injects
 * the Bearer token itself and, on a 401, replays once against a refreshed token — reusing the client's
 * single-flight `refreshAccessToken` rather than duplicating the refresh dance. `signal` (an AbortController)
 * lets the caller stop the stream.
 */
export async function* streamChat(
  body: ChatBody,
  signal: AbortSignal,
): AsyncGenerator<SseAssistantEvent, void, unknown> {
  const doFetch = () =>
    fetch('/api/assistant/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(getAccessToken() ? { Authorization: toBearerHeader(getAccessToken() as string) } : {}),
      },
      body: JSON.stringify(body),
      signal,
    });

  let res = await doFetch();
  if (res.status === 401) {
    const refreshed = await refreshAccessToken();
    if (refreshed) res = await doFetch();
  }
  if (!res.ok || !res.body) {
    throw await problemToError(res);
  }

  for await (const frame of parseSseStream(res.body)) {
    const event = toAssistantEvent(frame.event, frame.data);
    if (event) yield event;
  }
}
