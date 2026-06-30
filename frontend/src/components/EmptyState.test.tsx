import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen } from '../test/test-utils';
import { EmptyState } from './EmptyState';

describe('EmptyState', () => {
  it('renders the message and an optional CTA', () => {
    renderWithProviders(<EmptyState message="Nothing yet" cta={<button>Add</button>} />);
    expect(screen.getByText('Nothing yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add' })).toBeInTheDocument();
  });
});
