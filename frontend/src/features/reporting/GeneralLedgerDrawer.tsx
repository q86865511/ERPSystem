import { Badge, Drawer, Group, Loader, Table, Text } from '@mantine/core';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';
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
  const { t } = useI18n();
  const { data, isLoading } = useGeneralLedger(accountCode, asOf);
  const rows = data ?? [];
  const accountLabel = `${accountCode ?? ''}${accountName ? ` ${accountName}` : ''}`;

  return (
    <Drawer
      opened={accountCode != null}
      onClose={onClose}
      position="right"
      size="xl"
      title={t('reporting.generalLedger.title', { account: accountLabel })}
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
                  <Table.Th>{t('field.date')}</Table.Th>
                  <Table.Th>{t('reporting.generalLedger.entry')}</Table.Th>
                  <Table.Th>{t('reporting.generalLedger.source')}</Table.Th>
                  <Table.Th>{t('field.memo')}</Table.Th>
                  <Table.Th ta="right">{t('field.debit')}</Table.Th>
                  <Table.Th ta="right">{t('field.credit')}</Table.Th>
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
              {t('reporting.generalLedger.noEntries')}
            </Text>
          )}
        </>
      )}
    </Drawer>
  );
}
