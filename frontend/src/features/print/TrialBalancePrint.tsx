import { Stack, Table } from '@mantine/core';
import { useSearchParams } from 'react-router-dom';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';
import { useTrialBalance } from '../reporting/api';
import { PrintHeader, PrintLayout } from './PrintLayout';

export function TrialBalancePrint() {
  const { t } = useI18n();
  const [params] = useSearchParams();
  const asOf = params.get('asOf') || undefined;
  const tb = useTrialBalance(asOf);
  const d = tb.data;

  return (
    <PrintLayout ready={tb.isSuccess}>
      {d && (
        <Stack>
          <PrintHeader docType={t('print.trialBalance')} date={d.asOf ?? asOf} />
          <Table withTableBorder withColumnBorders>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('field.code')}</Table.Th>
                <Table.Th>{t('reporting.trialBalance.account')}</Table.Th>
                <Table.Th>{t('reporting.trialBalance.class')}</Table.Th>
                <Table.Th ta="right">{t('field.debit')}</Table.Th>
                <Table.Th ta="right">{t('field.credit')}</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(d.lines ?? []).map((r) => (
                <Table.Tr key={r.code}>
                  <Table.Td>{r.code}</Table.Td>
                  <Table.Td>{r.name}</Table.Td>
                  <Table.Td>{r.accountClass}</Table.Td>
                  <Table.Td ta="right">
                    <MoneyText value={r.debit} />
                  </Table.Td>
                  <Table.Td ta="right">
                    <MoneyText value={r.credit} />
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
            <Table.Tfoot>
              <Table.Tr>
                <Table.Th colSpan={3}>{t('field.total')}</Table.Th>
                <Table.Th ta="right">
                  <MoneyText value={d.totalDebit} />
                </Table.Th>
                <Table.Th ta="right">
                  <MoneyText value={d.totalCredit} />
                </Table.Th>
              </Table.Tr>
            </Table.Tfoot>
          </Table>
        </Stack>
      )}
    </PrintLayout>
  );
}
