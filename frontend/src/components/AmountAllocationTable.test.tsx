import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders, screen, userEvent } from '../test/test-utils';
import { AmountAllocationTable, type AllocationRow } from './AmountAllocationTable';

const rows: AllocationRow[] = [
  { id: 1, label: 'BILL-1', openBalance: '100.5000' },
  { id: 2, label: 'BILL-2', openBalance: '200.2500' },
];

describe('AmountAllocationTable', () => {
  it('shows the empty message when there are no open documents', () => {
    renderWithProviders(
      <AmountAllocationTable
        rows={[]}
        allocs={{}}
        onChange={() => {}}
        documentLabel="Doc"
        amountLabel="Amount"
        totalLabel="Total"
        emptyMessage="No open bills"
      />,
    );
    expect(screen.getByText('No open bills')).toBeInTheDocument();
  });

  it('renders the BigInt-exact cumulative total', () => {
    // 100.50 + 200.25 = 300.75 (summed at scale 4 via sumMoney, then formatted)
    renderWithProviders(
      <AmountAllocationTable
        rows={rows}
        allocs={{ 1: '100.50', 2: '200.25' }}
        onChange={() => {}}
        documentLabel="Doc"
        amountLabel="Amount"
        totalLabel="Total"
        emptyMessage="none"
      />,
    );
    expect(screen.getByText('300.75')).toBeInTheDocument();
  });

  it('reports an edit through onChange keyed by document id', async () => {
    const onChange = vi.fn();
    renderWithProviders(
      <AmountAllocationTable
        rows={rows}
        allocs={{}}
        onChange={onChange}
        documentLabel="Doc"
        amountLabel="Amount"
        totalLabel="Total"
        emptyMessage="none"
      />,
    );
    await userEvent.type(screen.getByLabelText('BILL-1'), '5');
    expect(onChange).toHaveBeenLastCalledWith({ 1: '5' });
  });
});
