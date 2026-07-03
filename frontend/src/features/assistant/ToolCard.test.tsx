import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders, screen, userEvent } from '../../test/test-utils';
import { ToolCard } from './ToolCard';
import type { UiToolCall } from './types';

const writeProposed: UiToolCall = {
  id: 'w1',
  name: 'create_po',
  kind: 'write',
  input: { partnerId: 7, qty: 5 },
  status: 'proposed',
};

describe('ToolCard', () => {
  it('shows the input summary and confirm/reject buttons for a pending write proposal', () => {
    renderWithProviders(
      <ToolCard tool={writeProposed} awaitingConfirmation onConfirm={vi.fn()} onReject={vi.fn()} />,
    );
    expect(screen.getByText('partnerId')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument();
  });

  it('fires onConfirm and onReject when the buttons are clicked', async () => {
    const onConfirm = vi.fn();
    const onReject = vi.fn();
    renderWithProviders(
      <ToolCard tool={writeProposed} awaitingConfirmation onConfirm={onConfirm} onReject={onReject} />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(onConfirm).toHaveBeenCalledOnce();
    await userEvent.click(screen.getByRole('button', { name: 'Reject' }));
    expect(onReject).toHaveBeenCalledOnce();
  });

  it('hides the confirm/reject buttons once the proposal is no longer awaiting confirmation', () => {
    renderWithProviders(
      <ToolCard
        tool={writeProposed}
        awaitingConfirmation={false}
        onConfirm={vi.fn()}
        onReject={vi.fn()}
      />,
    );
    expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument();
  });

  it('disables confirm/reject while resolving, so a fast double-click cannot fire either handler twice', async () => {
    const onConfirm = vi.fn();
    const onReject = vi.fn();
    renderWithProviders(
      <ToolCard
        tool={writeProposed}
        awaitingConfirmation
        resolving
        onConfirm={onConfirm}
        onReject={onReject}
      />,
    );
    const confirmBtn = screen.getByRole('button', { name: 'Confirm' });
    const rejectBtn = screen.getByRole('button', { name: 'Reject' });
    expect(confirmBtn).toBeDisabled();
    expect(rejectBtn).toBeDisabled();
    await userEvent.click(confirmBtn);
    await userEvent.click(rejectBtn);
    expect(onConfirm).not.toHaveBeenCalled();
    expect(onReject).not.toHaveBeenCalled();
  });

  it('reveals the result JSON when a successful read card is expanded', async () => {
    const success: UiToolCall = {
      id: 't1',
      name: 'list_items',
      kind: 'read',
      input: {},
      status: 'success',
      result: '{"count":3}',
    };
    renderWithProviders(
      <ToolCard tool={success} awaitingConfirmation={false} onConfirm={vi.fn()} onReject={vi.fn()} />,
    );
    const toggle = screen.getByRole('button', { name: 'Toggle result' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('{"count":3}')).toBeInTheDocument();
  });

  it('shows the error text for a failed tool', () => {
    const failed: UiToolCall = {
      id: 't1',
      name: 'do',
      kind: 'read',
      input: {},
      status: 'error',
      result: 'not found',
    };
    renderWithProviders(
      <ToolCard tool={failed} awaitingConfirmation={false} onConfirm={vi.fn()} onReject={vi.fn()} />,
    );
    expect(screen.getByText('not found')).toBeInTheDocument();
  });
});
