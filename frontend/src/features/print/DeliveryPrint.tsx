import { Stack, Table } from '@mantine/core';
import { useParams } from 'react-router-dom';
import { useI18n } from '../../i18n';
import { useItemMap, useItems } from '../masterdata/api';
import { useDelivery } from '../sales/api';
import { PrintHeader, PrintLayout } from './PrintLayout';

export function DeliveryPrint() {
  const { t } = useI18n();
  const { id } = useParams();
  const dlv = useDelivery(id ? Number(id) : null);
  const itemsQ = useItems();
  const items = useItemMap();
  const d = dlv.data;

  return (
    <PrintLayout ready={dlv.isSuccess && itemsQ.isSuccess}>
      {d && (
        <Stack>
          <PrintHeader docType={t('print.delivery')} number={d.deliveryNumber} date={d.postingDate} />
          <Table withTableBorder withColumnBorders>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('field.item')}</Table.Th>
                <Table.Th ta="right">{t('field.quantity')}</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(d.lines ?? []).map((l) => (
                <Table.Tr key={l.id}>
                  <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                  <Table.Td ta="right">{l.qtyShipped}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Stack>
      )}
    </PrintLayout>
  );
}
