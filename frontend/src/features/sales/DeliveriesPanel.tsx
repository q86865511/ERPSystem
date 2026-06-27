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
import { useItemMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateDelivery, useDeliveries, useDelivery, useOrder, useOrders } from './api';

const SHIPPABLE = new Set(['CONFIRMED', 'PARTIALLY_SHIPPED']);

export function DeliveriesPanel() {
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
      notifyError('Pick an SO, a stock location, and at least one quantity');
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
      notifySuccess(`Delivery ${d?.deliveryNumber} posted`);
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
            New delivery
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Delivery #</Table.Th>
            <Table.Th>Posting date</Table.Th>
            <Table.Th>Status</Table.Th>
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
                  View
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!deliveries.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No deliveries yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New delivery" size="xl">
        <Stack>
          <Group grow>
            <Select
              label="Sales order"
              placeholder="Confirmed SO"
              searchable
              data={soOptions}
              value={soId != null ? String(soId) : null}
              onChange={(v) => {
                setSoId(v ? Number(v) : null);
                setQtys({});
              }}
            />
            <LocationSelect
              label="Ship-from location"
              locationType="STOCK"
              value={locationId}
              onChange={setLocationId}
            />
            <DateInput label="Posting date" value={postingDate} onChange={setPostingDate} />
          </Group>

          {soId && so.isLoading && <Loader size="sm" />}
          {soId && so.data && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Ordered</Table.Th>
                  <Table.Th ta="right">Shipped</Table.Th>
                  <Table.Th ta="right" w={120}>
                    Ship now
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
              Cancel
            </Button>
            <Button onClick={submit} loading={create.isPending}>
              Post delivery
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={`Delivery ${detail.data?.deliveryNumber ?? ''}`}
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
                    <Table.Td ta="right">{l.qtyShipped}</Table.Td>
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
              Shipped at cost: Dr Deferred-COGS / Cr Finished Goods (COGS is recognised at invoicing).
            </Text>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
