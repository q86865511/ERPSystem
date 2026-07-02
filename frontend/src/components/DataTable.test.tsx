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

  it('sorts rows when a sortable column header is clicked', async () => {
    const sortableColumns: DataTableColumn<Row>[] = [{ key: 'name', label: 'Name', sortable: true }];
    renderWithProviders(
      <DataTable<Row>
        columns={sortableColumns}
        rows={[{ id: 1, name: 'Charlie' }, { id: 2, name: 'Alice' }, { id: 3, name: 'Bob' }]}
        rowKey={(r) => r.id}
        emptyMessage="none"
      />,
    );
    const firstBefore = screen.getAllByRole('row')[1]?.textContent;
    expect(firstBefore).toContain('Charlie');
    await userEvent.click(screen.getByRole('button', { name: /Name/ }));
    expect(screen.getAllByRole('row')[1]?.textContent).toContain('Alice'); // ascending
  });

  it('paginates when rows exceed pageSize and filters via the search box', async () => {
    const many: Row[] = Array.from({ length: 7 }, (_, i) => ({ id: i + 1, name: `Item ${i + 1}` }));
    renderWithProviders(
      <DataTable<Row> columns={columns} rows={many} rowKey={(r) => r.id} emptyMessage="none" searchable pageSize={5} />,
    );
    // 7 rows, pageSize 5 → only 5 body rows shown + a pagination control.
    expect(screen.getAllByRole('row')).toHaveLength(1 + 5); // header + 5
    expect(screen.getByText('1–5 of 7')).toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox', { name: /search/i }), 'Item 3');
    expect(screen.getByText('Item 3')).toBeInTheDocument();
    expect(screen.queryByText('Item 1')).not.toBeInTheDocument();
  });
});
