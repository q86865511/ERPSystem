import { Table, Text } from '@mantine/core';
import type { components } from '../../api/schema';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';

type StatementLine = components['schemas']['StatementLine'];

/** A labelled section of a financial statement: account lines plus a section total. */
export function StatementSection({
  title,
  lines,
  total,
}: {
  title: string;
  lines: StatementLine[];
  total: string | undefined;
}) {
  const { t } = useI18n();
  return (
    <div>
      <Text fw={600} mb={4}>
        {title}
      </Text>
      <Table>
        <Table.Tbody>
          {lines.map((l) => (
            <Table.Tr key={l.code}>
              <Table.Td w={80}>{l.code}</Table.Td>
              <Table.Td>{l.name}</Table.Td>
              <Table.Td ta="right">
                <MoneyText value={l.amount} />
              </Table.Td>
            </Table.Tr>
          ))}
          {lines.length === 0 && (
            <Table.Tr>
              <Table.Td colSpan={3}>
                <Text c="dimmed" size="sm">
                  {t('reporting.statement.noAccounts')}
                </Text>
              </Table.Td>
            </Table.Tr>
          )}
        </Table.Tbody>
        <Table.Tfoot>
          <Table.Tr>
            <Table.Th colSpan={2}>
              {t('reporting.statement.sectionTotal', { section: title })}
            </Table.Th>
            <Table.Th ta="right">
              <MoneyText value={total} />
            </Table.Th>
          </Table.Tr>
        </Table.Tfoot>
      </Table>
    </div>
  );
}
