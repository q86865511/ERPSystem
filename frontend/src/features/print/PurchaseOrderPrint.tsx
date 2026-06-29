import { Stack, Table } from '@mantine/core';
import { useParams } from 'react-router-dom';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';
import { useItemMap, useItems, usePartnerMap, usePartners } from '../masterdata/api';
import { useOrder } from '../purchasing/api';
import { PrintHeader, PrintLayout } from './PrintLayout';

export function PurchaseOrderPrint() {
  const { t } = useI18n();
  const { id } = useParams();
  const po = useOrder(id ? Number(id) : null);
  const itemsQ = useItems();
  const partnersQ = usePartners();
  const items = useItemMap();
  const partners = usePartnerMap();
  const d = po.data;

  return (
    <PrintLayout ready={po.isSuccess && itemsQ.isSuccess && partnersQ.isSuccess}>
      {d && (
        <Stack>
          <PrintHeader
            docType={t('print.purchaseOrder')}
            number={d.poNumber}
            date={d.orderDate}
            party={d.partnerId != null ? partners.get(d.partnerId) : undefined}
            partyLabel={t('field.vendor')}
          />
          <Table withTableBorder withColumnBorders>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('field.item')}</Table.Th>
                <Table.Th ta="right">{t('purchasing.po.ordered')}</Table.Th>
                <Table.Th ta="right">{t('field.unitPrice')}</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(d.lines ?? []).map((l) => (
                <Table.Tr key={l.id}>
                  <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                  <Table.Td ta="right">{l.qtyOrdered}</Table.Td>
                  <Table.Td ta="right">
                    <MoneyText value={l.unitPrice} />
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Stack>
      )}
    </PrintLayout>
  );
}
