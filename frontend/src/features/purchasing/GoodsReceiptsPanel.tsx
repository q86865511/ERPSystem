import { useState } from 'react';
import { Badge, Button, Drawer, Group, Loader, Modal, Select, Stack, Table, Text, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { CreateGrnRequest } from '../../api/types';
import { LocationSelect } from '../../components/EntitySelect';
import { MoneyText } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
import { useItemMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateReceipt, useOrder, useOrders, useReceipt, useReceipts } from './api';

const RECEIVABLE = new Set(['CONFIRMED', 'PARTIALLY_RECEIVED']);

export function GoodsReceiptsPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const receipts = useReceipts();
  const orders = useOrders();
  const items = useItemMap();
  const create = useCreateReceipt();
  const [opened, { open, close }] = useDisclosure(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useReceipt(detailId);

  const [poId, setPoId] = useState<number | null>(null);
  const [locationId, setLocationId] = useState<number | null>(null);
  const [postingDate, setPostingDate] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const [qtys, setQtys] = useState<Record<number, string>>({});
  const po = useOrder(poId);

  const openForm = () => {
    setPoId(null);
    setLocationId(null);
    setQtys({});
    open();
  };

  const poOptions = (orders.data ?? [])
    .filter((o) => o.status && RECEIVABLE.has(o.status))
    .map((o) => ({ value: String(o.id), label: o.poNumber ?? String(o.id) }));

  const submit = async () => {
    const lines = Object.entries(qtys)
      .filter(([, q]) => q && Number(q) > 0)
      .map(([poLineId, qty]) => ({ poLineId: Number(poLineId), qty }));
    if (!poId || !locationId || lines.length === 0) {
      notifyError(t('purchasing.grn.selectRequired'));
      return;
    }
    const body: CreateGrnRequest = {
      purchaseOrderId: poId,
      stockLocationId: locationId,
      postingDate: postingDate ?? undefined,
      lines,
    };
    try {
      const grn = await create.mutateAsync(body);
      notifySuccess(t('purchasing.grn.posted', { grnNumber: grn?.grnNumber ?? '' }));
      close();
      if (grn?.id != null) setDetailId(grn.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = receipts.data ?? [];
  return (
    <Stack>
      {canDo('purchasing.write') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            {t('purchasing.grn.new')}
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('purchasing.grn.grnNumber')}</Table.Th>
            <Table.Th>{t('purchasing.grn.postingDate')}</Table.Th>
            <Table.Th>{t('field.status')}</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((g) => (
            <Table.Tr key={g.id}>
              <Table.Td>{g.grnNumber}</Table.Td>
              <Table.Td>{g.postingDate}</Table.Td>
              <Table.Td>
                <StatusBadge status={g.status} />
              </Table.Td>
              <Table.Td ta="right">
                <Button size="xs" variant="subtle" onClick={() => setDetailId(g.id ?? null)}>
                  {t('common.view')}
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!receipts.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          {t('purchasing.grn.none')}
        </Text>
      )}

      <Modal opened={opened} onClose={close} title={t('purchasing.grn.new')} size="xl">
        <Stack>
          <Group grow>
            <Select
              label={t('purchasing.grn.purchaseOrder')}
              placeholder={t('purchasing.grn.confirmedPoPlaceholder')}
              searchable
              data={poOptions}
              value={poId != null ? String(poId) : null}
              onChange={(v) => {
                setPoId(v ? Number(v) : null);
                setQtys({});
              }}
            />
            <LocationSelect
              label={t('purchasing.grn.stockLocation')}
              locationType="STOCK"
              value={locationId}
              onChange={setLocationId}
            />
            <DateInput label={t('purchasing.grn.postingDate')} value={postingDate} onChange={setPostingDate} />
          </Group>

          {poId && po.isLoading && <Loader size="sm" />}
          {poId && po.data && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('purchasing.grn.ordered')}</Table.Th>
                  <Table.Th ta="right">{t('purchasing.grn.received')}</Table.Th>
                  <Table.Th ta="right" w={120}>
                    {t('purchasing.grn.receiveNow')}
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(po.data.lines ?? []).map((l) => (
                  <Table.Tr key={l.id}>
                    <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                    <Table.Td ta="right">{l.qtyOrdered}</Table.Td>
                    <Table.Td ta="right">{l.qtyReceived}</Table.Td>
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
              {t('purchasing.grn.postReceipt')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={t('purchasing.grn.drawerTitle', { grnNumber: detail.data?.grnNumber ?? '' })}
      >
        {detail.data && (
          <Stack>
            <StatusBadge status={detail.data.status} />
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('field.quantity')}</Table.Th>
                  <Table.Th ta="right">{t('field.unitCost')}</Table.Th>
                  <Table.Th>{t('purchasing.grn.posting')}</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(detail.data.lines ?? []).map((l) => (
                  <Table.Tr key={l.id}>
                    <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                    <Table.Td ta="right">{l.qtyReceived}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={l.unitCost} />
                    </Table.Td>
                    <Table.Td>
                      {l.journalEntryId != null && (
                        <Badge variant="light" size="sm">
                          {t('purchasing.grn.journalEntry', { id: l.journalEntryId })}
                        </Badge>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text size="xs" c="dimmed">
              {t('purchasing.grn.postingNote')}
            </Text>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
