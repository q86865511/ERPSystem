import { describe, expect, it } from 'vitest';
import { renderWithProviders, screen } from '../test/test-utils';
import { StatTile } from './StatTile';

describe('StatTile', () => {
  it('formats a money value by default', () => {
    renderWithProviders(<StatTile label="Assets" value="1234.5" />);
    expect(screen.getByText('Assets')).toBeInTheDocument();
    expect(screen.getByText('1,234.50')).toBeInTheDocument();
  });

  it('renders a plain value when money is false', () => {
    renderWithProviders(<StatTile label="Count" value="42" money={false} />);
    expect(screen.getByText('42')).toBeInTheDocument();
  });
});
