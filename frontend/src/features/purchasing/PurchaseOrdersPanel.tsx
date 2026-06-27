import { useState } from 'react';
import {
  ActionIcon,
  Button,
  Drawer,
  Group,
  Modal,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { CreatePoRequest } from '../../api/types';
import { ItemSelect, PartnerSelect } from '../../components/EntitySelect';
import { MoneyText } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
import { useItemMap, usePartnerMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useConfirmOrder, useCreateOrder, useOrder, useOrders } from './api';

type LineForm = { itemId: number | null; qtyOrdered: string; unitPrice: string };

function emptyLine(): LineForm {
  return { itemId: null, qtyOrdered: '', unitPrice: '' };
}

export function PurchaseOrdersPanel() {
  const { canDo } = useAuth();
  const { data, isLoading } = useOrders();
  const partners = usePartnerMap();
  const items = useItemMap();
  const create = useCreateOrder();
  const confirm = useConfirmOrder();
  const [opened, { open, close }] = useDisclosure(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useOrder(detailId);

  const form = useForm<{ partnerId: number | null; orderDate: string | null; lines: LineForm[] }>({
    initialValues: { partnerId: null, orderDate: dayjs().format('YYYY-MM-DD'), lines: [emptyLine()] },
    validate: {
      partnerId: (v) => (v != null ? null : 'Pick a vendor'),
    },
  });

  const submit = form.onSubmit(async (v) => {
    const lines = v.lines.filter((l) => l.itemId != null && l.qtyOrdered && l.unitPrice);
    if (lines.length === 0) {
      notifyError('Add at least one complete line');
      return;
    }
    const body: CreatePoRequest = {
      partnerId: v.partnerId ?? undefined,
      orderDate: v.orderDate ?? undefined,
      lines: lines.map((l) => ({
        itemId: l.itemId ?? undefined,
        qtyOrdered: l.qtyOrdered,
        unitPrice: l.unitPrice,
      })),
    };
    try {
      const po = await create.mutateAsync(body);
      notifySuccess(`Purchase order ${po?.poNumber} created`);
      close();
      form.reset();
      if (po?.id != null) setDetailId(po.id);
    } catch (e) {
      notifyError(e);
    }
  });

  const doConfirm = async (id: number) => {
    try {
      await confirm.mutateAsync(id);
      notifySuccess('Purchase order confirmed');
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = data ?? [];
  return (
    <Stack>
      {canDo('purchasing.write') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            New purchase order
          </Button>
        </Group>
      )}

      <Table.ScrollContainer minWidth={620}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>PO #</Table.Th>
              <Table.Th>Vendor</Table.Th>
              <Table.Th>Order date</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((po) => (
              <Table.Tr key={po.id}>
                <Table.Td>{po.poNumber}</Table.Td>
                <Table.Td>{po.partnerId != null ? (partners.get(po.partnerId) ?? po.partnerId) : '—'}</Table.Td>
                <Table.Td>{po.orderDate}</Table.Td>
                <Table.Td>
                  <StatusBadge status={po.status} />
                </Table.Td>
                <Table.Td ta="right">
                  <Button size="xs" variant="subtle" onClick={() => setDetailId(po.id ?? null)}>
                    View
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {!isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No purchase orders yet.
        </Text>
      )}

      {/* Create modal */}
      <Modal opened={opened} onClose={close} title="New purchase order" size="xl">
        <form onSubmit={submit}>
          <Stack>
            <Group grow>
              <PartnerSelect
                label="Vendor"
                vendor
                required
                value={form.values.partnerId}
                onChange={(id) => form.setFieldValue('partnerId', id)}
                error={form.errors.partnerId}
              />
              <DateInput label="Order date" value={form.values.orderDate} onChange={(d) => form.setFieldValue('orderDate', d)} />
            </Group>

            <Text fw={600} size="sm">
              Lines
            </Text>
            {form.values.lines.map((_, i) => (
              <Group key={i} align="flex-end" wrap="nowrap">
                <ItemSelect
                  label={i === 0 ? 'Item' : undefined}
                  value={form.values.lines[i]?.itemId ?? null}
                  onChange={(id) => form.setFieldValue(`lines.${i}.itemId`, id)}
                  style={{ flex: 1 }}
                />
                <TextInput
                  label={i === 0 ? 'Qty' : undefined}
                  w={100}
                  {...form.getInputProps(`lines.${i}.qtyOrdered`)}
                />
                <TextInput
                  label={i === 0 ? 'Unit price' : undefined}
                  w={120}
                  {...form.getInputProps(`lines.${i}.unitPrice`)}
                />
                <ActionIcon
                  variant="subtle"
                  color="red"
                  disabled={form.values.lines.length === 1}
                  onClick={() => form.removeListItem('lines', i)}
                >
                  <IconTrash size={16} />
                </ActionIcon>
              </Group>
            ))}
            <Group>
              <Button
                size="xs"
                variant="default"
                leftSection={<IconPlus size={14} />}
                onClick={() => form.insertListItem('lines', emptyLine())}
              >
                Add line
              </Button>
            </Group>

            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={close}>
                Cancel
              </Button>
              <Button type="submit" loading={create.isPending}>
                Create
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>

      {/* Detail drawer */}
      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={`Purchase order ${detail.data?.poNumber ?? ''}`}
      >
        {detail.data && (
          <Stack>
            <Group>
              <StatusBadge status={detail.data.status} />
              <Text c="dimmed" size="sm">
                {detail.data.partnerId != null ? partners.get(detail.data.partnerId) : ''} ·{' '}
                {detail.data.orderDate}
              </Text>
            </Group>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Ordered</Table.Th>
                  <Table.Th ta="right">Received</Table.Th>
                  <Table.Th ta="right">Billed</Table.Th>
                  <Table.Th ta="right">Unit price</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(detail.data.lines ?? []).map((l) => (
                  <Table.Tr key={l.id}>
                    <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                    <Table.Td ta="right">{l.qtyOrdered}</Table.Td>
                    <Table.Td ta="right">{l.qtyReceived}</Table.Td>
                    <Table.Td ta="right">{l.qtyBilled}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={l.unitPrice} />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            {detail.data.status === 'DRAFT' && canDo('purchasing.write') && (
              <Group justify="flex-end">
                <Button loading={confirm.isPending} onClick={() => detailId && doConfirm(detailId)}>
                  Confirm order
                </Button>
              </Group>
            )}
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
