import { Badge, Drawer, Group, Loader, Table, Text } from '@mantine/core';
import { MoneyText } from '../../components/Money';
import { useGeneralLedger } from './api';

const nonZero = (v: string | undefined) => v != null && Number(v) !== 0;

/** Reusable account drill-down: the posted ledger lines for an account as of a date. */
export function GeneralLedgerDrawer({
  accountCode,
  accountName,
  asOf,
  onClose,
}: {
  accountCode: string | null;
  accountName?: string;
  asOf?: string;
  onClose: () => void;
}) {
  const { data, isLoading } = useGeneralLedger(accountCode, asOf);
  const rows = data ?? [];

  return (
    <Drawer
      opened={accountCode != null}
      onClose={onClose}
      position="right"
      size="xl"
      title={`General ledger — ${accountCode ?? ''}${accountName ? ` ${accountName}` : ''}`}
    >
      {isLoading ? (
        <Group justify="center" py="xl">
          <Loader />
        </Group>
      ) : (
        <>
          <Table.ScrollContainer minWidth={560}>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Date</Table.Th>
                  <Table.Th>Entry</Table.Th>
                  <Table.Th>Source</Table.Th>
                  <Table.Th>Memo</Table.Th>
                  <Table.Th ta="right">Debit</Table.Th>
                  <Table.Th ta="right">Credit</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {rows.map((l, idx) => (
                  <Table.Tr key={`${l.entryId}-${idx}`}>
                    <Table.Td>{l.postingDate}</Table.Td>
                    <Table.Td>#{l.entryNo}</Table.Td>
                    <Table.Td>
                      {l.sourceDocType ? (
                        <Badge variant="light" size="sm">
                          {l.sourceDocType} {l.sourceDocId}
                        </Badge>
                      ) : (
                        '—'
                      )}
                    </Table.Td>
                    <Table.Td>{l.memo}</Table.Td>
                    <Table.Td ta="right">{nonZero(l.debit) ? <MoneyText value={l.debit} /> : ''}</Table.Td>
                    <Table.Td ta="right">
                      {nonZero(l.credit) ? <MoneyText value={l.credit} /> : ''}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          {rows.length === 0 && (
            <Text c="dimmed" ta="center" py="md">
              No entries for this account.
            </Text>
          )}
        </>
      )}
    </Drawer>
  );
}
