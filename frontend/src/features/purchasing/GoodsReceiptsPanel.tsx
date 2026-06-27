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
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateReceipt, useOrder, useOrders, useReceipt, useReceipts } from './api';

const RECEIVABLE = new Set(['CONFIRMED', 'PARTIALLY_RECEIVED']);

export function GoodsReceiptsPanel() {
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
      notifyError('Pick a PO, a stock location, and at least one quantity');
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
      notifySuccess(`Goods receipt ${grn?.grnNumber} posted`);
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
            New goods receipt
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>GRN #</Table.Th>
            <Table.Th>Posting date</Table.Th>
            <Table.Th>Status</Table.Th>
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
                  View
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!receipts.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No goods receipts yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New goods receipt" size="xl">
        <Stack>
          <Group grow>
            <Select
              label="Purchase order"
              placeholder="Confirmed PO"
              searchable
              data={poOptions}
              value={poId != null ? String(poId) : null}
              onChange={(v) => {
                setPoId(v ? Number(v) : null);
                setQtys({});
              }}
            />
            <LocationSelect
              label="Stock location"
              locationType="STOCK"
              value={locationId}
              onChange={setLocationId}
            />
            <DateInput label="Posting date" value={postingDate} onChange={setPostingDate} />
          </Group>

          {poId && po.isLoading && <Loader size="sm" />}
          {poId && po.data && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Ordered</Table.Th>
                  <Table.Th ta="right">Received</Table.Th>
                  <Table.Th ta="right" w={120}>
                    Receive now
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
              Cancel
            </Button>
            <Button onClick={submit} loading={create.isPending}>
              Post receipt
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={`Goods receipt ${detail.data?.grnNumber ?? ''}`}
      >
        {detail.data && (
          <Stack>
            <StatusBadge status={detail.data.status} />
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Qty</Table.Th>
                  <Table.Th ta="right">Unit cost</Table.Th>
                  <Table.Th>Posting</Table.Th>
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
                          JE #{l.journalEntryId}
                        </Badge>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text size="xs" c="dimmed">
              Each line posted Dr Inventory / Cr GR-IR at moving-average cost.
            </Text>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
