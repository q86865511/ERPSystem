import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders, screen, userEvent } from '../test/test-utils';
import { StateButton } from './StateButton';

describe('StateButton', () => {
  it('fires onClick when enabled', async () => {
    const onClick = vi.fn();
    renderWithProviders(<StateButton label="Confirm" onClick={onClick} />);
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('is disabled and does not fire when disabled with a reason', async () => {
    const onClick = vi.fn();
    renderWithProviders(
      <StateButton label="Confirm" onClick={onClick} disabled disabledReason="Only draft orders" />,
    );
    const btn = screen.getByRole('button', { name: 'Confirm' });
    expect(btn).toBeDisabled();
    await userEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });
});
