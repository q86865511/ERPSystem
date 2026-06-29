import { useState } from 'react';
import { Badge, Button, Drawer, Group, Loader, Modal, Select, Stack, Table, Text, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { CreateDeliveryRequest } from '../../api/types';
import { LocationSelect } from '../../components/EntitySelect';
import { MoneyText } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
import { useI18n } from '../../i18n';
import { useItemMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateDelivery, useDeliveries, useDelivery, useOrder, useOrders } from './api';

const SHIPPABLE = new Set(['CONFIRMED', 'PARTIALLY_SHIPPED']);

export function DeliveriesPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const deliveries = useDeliveries();
  const orders = useOrders();
  const items = useItemMap();
  const create = useCreateDelivery();
  const [opened, { open, close }] = useDisclosure(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useDelivery(detailId);

  const [soId, setSoId] = useState<number | null>(null);
  const [locationId, setLocationId] = useState<number | null>(null);
  const [postingDate, setPostingDate] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const [qtys, setQtys] = useState<Record<number, string>>({});
  const so = useOrder(soId);

  const openForm = () => {
    setSoId(null);
    setLocationId(null);
    setQtys({});
    open();
  };

  const soOptions = (orders.data ?? [])
    .filter((o) => o.status && SHIPPABLE.has(o.status))
    .map((o) => ({ value: String(o.id), label: o.soNumber ?? String(o.id) }));

  const submit = async () => {
    const lines = Object.entries(qtys)
      .filter(([, q]) => q && Number(q) > 0)
      .map(([soLineId, qty]) => ({ soLineId: Number(soLineId), qty }));
    if (!soId || !locationId || lines.length === 0) {
      notifyError(t('sales.delivery.needInput'));
      return;
    }
    const body: CreateDeliveryRequest = {
      salesOrderId: soId,
      stockLocationId: locationId,
      postingDate: postingDate ?? undefined,
      lines,
    };
    try {
      const d = await create.mutateAsync(body);
      notifySuccess(t('sales.delivery.posted', { deliveryNumber: d?.deliveryNumber ?? '' }));
      close();
      if (d?.id != null) setDetailId(d.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = deliveries.data ?? [];
  return (
    <Stack>
      {canDo('sales.delivery') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            {t('sales.delivery.new')}
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('sales.delivery.deliveryNumber')}</Table.Th>
            <Table.Th>{t('sales.delivery.postingDate')}</Table.Th>
            <Table.Th>{t('field.status')}</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((d) => (
            <Table.Tr key={d.id}>
              <Table.Td>{d.deliveryNumber}</Table.Td>
              <Table.Td>{d.postingDate}</Table.Td>
              <Table.Td>
                <StatusBadge status={d.status} />
              </Table.Td>
              <Table.Td ta="right">
                <Button size="xs" variant="subtle" onClick={() => setDetailId(d.id ?? null)}>
                  {t('common.view')}
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!deliveries.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          {t('sales.delivery.empty')}
        </Text>
      )}

      <Modal opened={opened} onClose={close} title={t('sales.delivery.modalTitle')} size="xl">
        <Stack>
          <Group grow>
            <Select
              label={t('sales.delivery.salesOrder')}
              placeholder={t('sales.delivery.soPlaceholder')}
              searchable
              data={soOptions}
              value={soId != null ? String(soId) : null}
              onChange={(v) => {
                setSoId(v ? Number(v) : null);
                setQtys({});
              }}
            />
            <LocationSelect
              label={t('sales.delivery.shipFrom')}
              locationType="STOCK"
              value={locationId}
              onChange={setLocationId}
            />
            <DateInput label={t('sales.delivery.postingDate')} value={postingDate} onChange={setPostingDate} />
          </Group>

          {soId && so.isLoading && <Loader size="sm" />}
          {soId && so.data && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('sales.delivery.ordered')}</Table.Th>
                  <Table.Th ta="right">{t('sales.delivery.shipped')}</Table.Th>
                  <Table.Th ta="right" w={120}>
                    {t('sales.delivery.shipNow')}
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(so.data.lines ?? []).map((l) => (
                  <Table.Tr key={l.id}>
                    <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                    <Table.Td ta="right">{l.qtyOrdered}</Table.Td>
                    <Table.Td ta="right">{l.qtyShipped}</Table.Td>
                    <Table.Td>
                      <TextInput
                        size="xs"
                        value={l.id != null ? (qtys[l.id] ?? '') : ''}
                        onChange={(e) =>
                          l.id != null &&
                          setQtys((q) => ({ ...q, [l.id as number]: e.currentTarget.value }))
                        }
                      />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}

          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button onClick={submit} loading={create.isPending}>
              {t('sales.delivery.post')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={t('sales.delivery.drawerTitle', { deliveryNumber: detail.data?.deliveryNumber ?? '' })}
      >
        {detail.data && (
          <Stack>
            <StatusBadge status={detail.data.status} />
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('sales.delivery.qty')}</Table.Th>
                  <Table.Th ta="right">{t('field.unitCost')}</Table.Th>
                  <Table.Th>{t('sales.delivery.posting')}</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(detail.data.lines ?? []).map((l) => (
                  <Table.Tr key={l.id}>
                    <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                    <Table.Td ta="right">{l.qtyShipped}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={l.unitCost} />
                    </Table.Td>
                    <Table.Td>
                      {l.journalEntryId != null && (
                        <Badge variant="light" size="sm">
                          {t('sales.je', { id: l.journalEntryId })}
                        </Badge>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text size="xs" c="dimmed">
              {t('sales.delivery.costNote')}
            </Text>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
