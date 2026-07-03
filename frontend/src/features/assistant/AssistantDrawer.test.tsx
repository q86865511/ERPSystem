import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders, screen, userEvent } from '../../test/test-utils';
import { AssistantDrawer } from './AssistantDrawer';
import type { UiMessage } from './types';

// Mocks the hook so this suite is purely about the Drawer's own rendering/wiring (preset chips shown/hidden,
// clicking a chip calling send with the right args) — the hook's own stream/reducer logic is covered by
// useAssistantChat.test.tsx and assistantReducer.test.ts.
const { useAssistantChatMock } = vi.hoisted(() => ({ useAssistantChatMock: vi.fn() }));
vi.mock('./useAssistantChat', () => ({ useAssistantChat: useAssistantChatMock }));

function baseChat(overrides: Partial<ReturnType<typeof useAssistantChatMock>> = {}) {
  return {
    messages: [] as UiMessage[],
    streaming: false,
    pending: null,
    error: null,
    send: vi.fn(),
    resolveConfirmation: vi.fn(),
    stop: vi.fn(),
    reset: vi.fn(),
    ...overrides,
  };
}

describe('AssistantDrawer — analysis preset chips', () => {
  it('shows both preset chips when the conversation is empty', () => {
    useAssistantChatMock.mockReturnValue(baseChat());
    renderWithProviders(<AssistantDrawer opened onClose={vi.fn()} />);

    expect(screen.getByText('Reconciliation health check')).toBeInTheDocument();
    expect(screen.getByText('Gross margin analysis')).toBeInTheDocument();
  });

  it('hides the preset chips once the conversation has messages', () => {
    useAssistantChatMock.mockReturnValue(
      baseChat({
        messages: [{ kind: 'user', id: 'm1', text: 'hi' }],
      }),
    );
    renderWithProviders(<AssistantDrawer opened onClose={vi.fn()} />);

    expect(screen.queryByText('Reconciliation health check')).not.toBeInTheDocument();
    expect(screen.queryByText('Gross margin analysis')).not.toBeInTheDocument();
  });

  it('disables the preset chips while a stream is in flight, even with no messages yet', () => {
    useAssistantChatMock.mockReturnValue(baseChat({ streaming: true }));
    renderWithProviders(<AssistantDrawer opened onClose={vi.fn()} />);

    // messages is still empty, but streaming — chips must not invite a second concurrent turn.
    // (The empty-state text itself is gated on `messages.length === 0`, independent of streaming; only the
    // chip's own <button> is guarded via its `disabled` prop.)
    const chip = screen.getByRole('button', { name: 'Reconciliation health check' });
    expect(chip).toBeDisabled();
  });

  it('still shows the preset chips after an error, as long as the conversation is empty', () => {
    // A preset's very first turn can fail (rate limit, dropped connection, etc.) before any message lands —
    // the chips must stay visible so the user can retry, rather than being gated on "no error".
    useAssistantChatMock.mockReturnValue(
      baseChat({ error: { title: 'Something went wrong' } }),
    );
    renderWithProviders(<AssistantDrawer opened onClose={vi.fn()} />);

    expect(screen.getByText('Reconciliation health check')).toBeInTheDocument();
    expect(screen.getByText('Gross margin analysis')).toBeInTheDocument();
  });

  it('clicking a preset chip sends its canned prompt tagged with that preset', async () => {
    const send = vi.fn();
    useAssistantChatMock.mockReturnValue(baseChat({ send }));
    renderWithProviders(<AssistantDrawer opened onClose={vi.fn()} />);

    await userEvent.click(screen.getByText('Reconciliation health check'));

    expect(send).toHaveBeenCalledWith(
      'Are the books reconciled? If not, diagnose why.',
      'reconciliation',
    );
  });

  it('clicking the margin chip sends the margin prompt tagged with the margin preset', async () => {
    const send = vi.fn();
    useAssistantChatMock.mockReturnValue(baseChat({ send }));
    renderWithProviders(<AssistantDrawer opened onClose={vi.fn()} />);

    await userEvent.click(screen.getByText('Gross margin analysis'));

    expect(send).toHaveBeenCalledWith(
      'Why did gross margin change compared to last month?',
      'margin',
    );
  });
});
