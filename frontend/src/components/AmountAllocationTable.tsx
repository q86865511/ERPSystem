import { Table, Text, TextInput } from '@mantine/core';
import { formatMoney, MoneyText, sumMoney } from './Money';

export interface AllocationRow {
  id: number;
  openBalance?: string;
  label: string;
}

/**
 * Controlled payment-allocation table: one editable amount per open document (bill / invoice), with a
 * BigInt-exact cumulative total (via `sumMoney`) so the sum equals the backend BigDecimal. Money stays a
 * STRING end-to-end; `Number()` is only ever used for a ">0" test by callers. Shows an empty row when there
 * are no open documents. The parent owns `allocs` and builds the submit payload.
 */
export function AmountAllocationTable({
  rows,
  allocs,
  onChange,
  documentLabel,
  amountLabel,
  totalLabel,
  emptyMessage,
}: {
  rows: AllocationRow[];
  allocs: Record<number, string>;
  onChange: (allocs: Record<number, string>) => void;
  documentLabel: string;
  amountLabel: string;
  totalLabel: string;
  emptyMessage: string;
}) {
  const total = sumMoney(Object.values(allocs));
  const setAlloc = (id: number, v: string) => onChange({ ...allocs, [id]: v });

  return (
    <Table>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>{documentLabel}</Table.Th>
          <Table.Th ta="right">{amountLabel}</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {rows.length === 0 ? (
          <Table.Tr>
            <Table.Td colSpan={2}>
              <Text c="dimmed" ta="center">
                {emptyMessage}
              </Text>
            </Table.Td>
          </Table.Tr>
        ) : (
          rows.map((r) => (
            <Table.Tr key={r.id}>
              <Table.Td>{r.label}</Table.Td>
              <Table.Td ta="right">
                <TextInput
                  size="xs"
                  aria-label={r.label}
                  placeholder={formatMoney(r.openBalance)}
                  value={allocs[r.id] ?? ''}
                  onChange={(e) => setAlloc(r.id, e.currentTarget.value)}
                />
              </Table.Td>
            </Table.Tr>
          ))
        )}
      </Table.Tbody>
      <Table.Tfoot>
        <Table.Tr>
          <Table.Th>{totalLabel}</Table.Th>
          <Table.Th ta="right">
            <MoneyText value={total} />
          </Table.Th>
        </Table.Tr>
      </Table.Tfoot>
    </Table>
  );
}
