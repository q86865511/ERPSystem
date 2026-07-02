import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders, screen, userEvent } from '../test/test-utils';
import { DataTable, type DataTableColumn } from './DataTable';

interface Row {
  id: number;
  name: string;
}
const columns: DataTableColumn<Row>[] = [{ key: 'name', label: 'Name' }];

describe('DataTable', () => {
  it('shows a skeleton while loading', () => {
    const { container } = renderWithProviders(
      <DataTable<Row> columns={columns} rows={[]} rowKey={(r) => r.id} isLoading emptyMessage="none" />,
    );
    expect(container.querySelector('.mantine-Skeleton-root')).toBeInTheDocument();
  });

  it('shows the empty message when there are no rows', () => {
    renderWithProviders(
      <DataTable<Row> columns={columns} rows={[]} rowKey={(r) => r.id} emptyMessage="Nothing here" />,
    );
    expect(screen.getByText('Nothing here')).toBeInTheDocument();
  });

  it('renders a semantic table and the row cells', () => {
    renderWithProviders(
      <DataTable<Row> columns={columns} rows={[{ id: 1, name: 'Acme' }]} rowKey={(r) => r.id} emptyMessage="none" />,
    );
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByText('Acme')).toBeInTheDocument();
  });

  it('fires onRowClick via the accessible (keyboard-focusable) chevron button', async () => {
    const onRowClick = vi.fn();
    renderWithProviders(
      <DataTable<Row>
        columns={columns}
        rows={[{ id: 1, name: 'Acme' }]}
        rowKey={(r) => r.id}
        emptyMessage="none"
        onRowClick={onRowClick}
        rowActionLabel="open"
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'open' }));
    expect(onRowClick).toHaveBeenCalledWith({ id: 1, name: 'Acme' });
  });
});
