import { Stack, Table, Text } from '@mantine/core';
import { useParams } from 'react-router-dom';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';
import { useItemMap, useItems, usePartnerMap, usePartners } from '../masterdata/api';
import { useInvoice } from '../sales/api';
import { PrintHeader, PrintLayout } from './PrintLayout';

export function SalesInvoicePrint() {
  const { t } = useI18n();
  const { id } = useParams();
  const inv = useInvoice(id ? Number(id) : null);
  const itemsQ = useItems();
  const partnersQ = usePartners();
  const items = useItemMap();
  const partners = usePartnerMap();
  const d = inv.data;

  return (
    <PrintLayout ready={inv.isSuccess && itemsQ.isSuccess && partnersQ.isSuccess}>
      {d && (
        <Stack>
          <PrintHeader
            docType={t('print.salesInvoice')}
            number={d.invoiceNumber}
            date={d.postingDate}
            party={d.partnerId != null ? partners.get(d.partnerId) : undefined}
            partyLabel={t('field.customer')}
          />
          <Table withTableBorder withColumnBorders>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('field.item')}</Table.Th>
                <Table.Th ta="right">{t('field.quantity')}</Table.Th>
                <Table.Th ta="right">{t('field.net')}</Table.Th>
                <Table.Th ta="right">{t('field.vat')}</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(d.lines ?? []).map((l) => (
                <Table.Tr key={l.id}>
                  <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                  <Table.Td ta="right">{l.qty}</Table.Td>
                  <Table.Td ta="right">
                    <MoneyText value={l.lineNet} />
                  </Table.Td>
                  <Table.Td ta="right">
                    <MoneyText value={l.lineVat} />
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          <Stack gap={2} align="flex-end">
            <Text size="sm">
              {t('sales.invoice.goods')}: <MoneyText value={d.goodsAmount} />
            </Text>
            <Text size="sm">
              {t('field.vat')}: <MoneyText value={d.vatAmount} />
            </Text>
            <Text fw={700}>
              {t('field.gross')}: <MoneyText value={d.grossAmount} />
            </Text>
          </Stack>
        </Stack>
      )}
    </PrintLayout>
  );
}
