import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen } from '../test/test-utils';
import { PageHeader } from './PageHeader';

describe('PageHeader', () => {
  it('renders title plus the additive subtitle/status slots', () => {
    renderWithProviders(<PageHeader title="Purchasing" subtitle="P2P" status={<span>OK</span>} />);
    expect(screen.getByRole('heading', { name: 'Purchasing' })).toBeInTheDocument();
    expect(screen.getByText('P2P')).toBeInTheDocument();
    expect(screen.getByText('OK')).toBeInTheDocument();
  });
});
