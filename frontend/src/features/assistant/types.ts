/**
 * Assistant domain types. Two parallel representations of the conversation:
 *  - `Message` (+ `ContentBlock`) — the raw Anthropic-shaped blocks POSTed back to the stateless backend so
 *    it can replay history (the frontend is the source of truth for the transcript).
 *  - `UiMessage` (+ `UiToolCall`) — the flattened, render-friendly view the Drawer paints.
 * The reducer keeps both in sync as SSE events and user actions arrive.
 */

/** Tool classification from the backend's `tool_call.kind`: reads run automatically, writes need approval. */
export type ToolKind = 'read' | 'write';

/** ---- Wire blocks (backend contract) ---- */
export interface TextBlock {
  type: 'text';
  text: string;
}
export interface ToolUseBlock {
  type: 'tool_use';
  id: string;
  name: string;
  input: unknown;
}
export interface ToolResultBlock {
  type: 'tool_result';
  toolUseId: string;
  content: string;
  isError: boolean;
}
export type ContentBlock = TextBlock | ToolUseBlock | ToolResultBlock;

export interface Message {
  role: 'user' | 'assistant';
  content: ContentBlock[];
}

export interface Decision {
  toolUseId: string;
  approved: boolean;
}

/** ---- SSE events (backend contract) ---- */
export type StopReason = 'end_turn' | 'awaiting_confirmation' | 'max_turns' | 'error';

export type SseAssistantEvent =
  | { type: 'text_delta'; text: string }
  | { type: 'tool_call'; id: string; name: string; input: unknown; kind: ToolKind }
  | { type: 'tool_result'; id: string; ok: boolean; result: string }
  | { type: 'awaiting_confirmation'; id: string; name: string; input: unknown }
  | { type: 'done'; stopReason: StopReason; usage?: { inputTokens: number; outputTokens: number } }
  | { type: 'error'; title: string; detail?: string; status?: number };

/** ---- UI view model ---- */
export type ToolCardStatus = 'proposed' | 'running' | 'success' | 'error';

export interface UiToolCall {
  id: string;
  name: string;
  kind: ToolKind;
  input: unknown;
  status: ToolCardStatus;
  /** Result JSON/text once the tool has run (success or error). */
  result?: string;
}

/** A rendered conversation entry: a user text bubble, or an assistant turn (text + any tool cards). */
export type UiMessage =
  | { kind: 'user'; id: string; text: string }
  | {
      kind: 'assistant';
      id: string;
      text: string;
      tools: UiToolCall[];
      /**
       * Commit cursor for `commitDraft` (see assistantReducer.ts). A HITL turn commits this draft to
       * `conversation` in two steps — once when the user confirms/rejects a write (so the resume POST can
       * carry it), and again when the (resumed) turn reaches `done` — so the reducer must remember exactly
       * how much of this draft it already pushed, or the second commit would duplicate the assistant
       * text/tool_use blocks and any already-committed tool_result. `undefined` means nothing committed yet.
       */
      committed?: {
        /** Length of `text` already included in a committed assistant block (`text` only ever grows, so
         *  this is always a prefix boundary — the tail past this length is what a later commit adds). */
        textLen: number;
        /** Number of leading `tools` entries whose `tool_use` block is already in `conversation`. */
        toolUseCount: number;
        /** Ids of tools whose `tool_result` is already in `conversation`. */
        resultIds: string[];
      };
    };

/** A pending write-tool approval that pauses the stream until the user confirms or rejects. */
export interface PendingConfirmation {
  toolUseId: string;
  name: string;
  input: unknown;
}
