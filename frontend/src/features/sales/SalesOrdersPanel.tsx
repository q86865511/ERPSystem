import { useState } from 'react';
import { ActionIcon, Button, Group, Modal, Stack, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { CreateSoRequest } from '../../api/types';
import { ItemSelect, PartnerSelect } from '../../components/EntitySelect';
import { DataTable, DetailDrawer, MoneyText, StateButton, StatusBadge } from '../../components';
import type { DataTableColumn } from '../../components';
import { useI18n } from '../../i18n';
import { useItemMap, usePartnerMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useConfirmOrder, useCreateOrder, useOrder, useOrders } from './api';

type LineForm = { itemId: number | null; qtyOrdered: string; unitPrice: string };
const emptyLine = (): LineForm => ({ itemId: null, qtyOrdered: '', unitPrice: '' });

export function SalesOrdersPanel() {
  const { t } = useI18n();
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
    validate: { partnerId: (v) => (v != null ? null : t('sales.order.pickCustomer')) },
  });

  const submit = form.onSubmit(async (v) => {
    const lines = v.lines.filter((l) => l.itemId != null && l.qtyOrdered && l.unitPrice);
    if (lines.length === 0) {
      notifyError(t('sales.order.needLine'));
      return;
    }
    const body: CreateSoRequest = {
      partnerId: v.partnerId ?? undefined,
      orderDate: v.orderDate ?? undefined,
      lines: lines.map((l) => ({
        itemId: l.itemId ?? undefined,
        qtyOrdered: l.qtyOrdered,
        unitPrice: l.unitPrice,
      })),
    };
    try {
      const so = await create.mutateAsync(body);
      notifySuccess(t('sales.order.created', { soNumber: so?.soNumber ?? '' }));
      close();
      form.reset();
      if (so?.id != null) setDetailId(so.id);
    } catch (e) {
      notifyError(e);
    }
  });

  const doConfirm = async (id: number) => {
    try {
      await confirm.mutateAsync(id);
      notifySuccess(t('sales.order.confirmed'));
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'soNumber', label: t('sales.order.soNumber') },
    {
      key: 'customer',
      label: t('field.customer'),
      render: (so) => (so.partnerId != null ? (partners.get(so.partnerId) ?? so.partnerId) : '—'),
    },
    { key: 'orderDate', label: t('sales.order.orderDate') },
    { key: 'status', label: t('field.status'), render: (so) => <StatusBadge status={so.status} /> },
  ];

  return (
    <Stack>
      {canDo('sales.order') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('sales.order.new')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(so) => so.id ?? so.soNumber ?? ''}
        isLoading={isLoading}
        emptyMessage={t('sales.order.empty')}
        onRowClick={(so) => setDetailId(so.id ?? null)}
        minWidth={620}
      />

      <Modal opened={opened} onClose={close} title={t('sales.order.modalTitle')} size="xl">
        <form onSubmit={submit}>
          <Stack>
            <Group grow>
              <PartnerSelect
                label={t('field.customer')}
                customer
                required
                value={form.values.partnerId}
                onChange={(id) => form.setFieldValue('partnerId', id)}
                error={form.errors.partnerId}
              />
              <DateInput label={t('sales.order.orderDate')} value={form.values.orderDate} onChange={(d) => form.setFieldValue('orderDate', d)} />
            </Group>
            <Text fw={600} size="sm">
              {t('sales.order.lines')}
            </Text>
            {form.values.lines.map((_, i) => (
              <Group key={i} align="flex-end" wrap="nowrap">
                <ItemSelect
                  label={i === 0 ? t('field.item') : undefined}
                  value={form.values.lines[i]?.itemId ?? null}
                  onChange={(id) => form.setFieldValue(`lines.${i}.itemId`, id)}
                  style={{ flex: 1 }}
                />
                <TextInput label={i === 0 ? t('sales.order.qty') : undefined} w={100} {...form.getInputProps(`lines.${i}.qtyOrdered`)} />
                <TextInput label={i === 0 ? t('field.unitPrice') : undefined} w={120} {...form.getInputProps(`lines.${i}.unitPrice`)} />
                <ActionIcon
                  variant="subtle"
                  color="red"
                  aria-label={t('common.removeLine')}
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
                {t('common.addLine')}
              </Button>
            </Group>
            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={close}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" loading={create.isPending}>
                {t('common.create')}
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>

      <DetailDrawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        title={t('sales.order.drawerTitle', { soNumber: detail.data?.soNumber ?? '' })}
        footer={
          detail.data?.status === 'DRAFT' && canDo('sales.order') ? (
            <Group justify="flex-end">
              <StateButton
                label={t('sales.order.confirm')}
                loading={confirm.isPending}
                onClick={() => {
                  if (detailId != null) void doConfirm(detailId);
                }}
              />
            </Group>
          ) : undefined
        }
      >
        {detail.data && (
          <>
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
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('sales.order.ordered')}</Table.Th>
                  <Table.Th ta="right">{t('sales.order.shipped')}</Table.Th>
                  <Table.Th ta="right">{t('sales.order.invoiced')}</Table.Th>
                  <Table.Th ta="right">{t('field.unitPrice')}</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(detail.data.lines ?? []).map((l) => (
                  <Table.Tr key={l.id}>
                    <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                    <Table.Td ta="right">{l.qtyOrdered}</Table.Td>
                    <Table.Td ta="right">{l.qtyShipped}</Table.Td>
                    <Table.Td ta="right">{l.qtyInvoiced}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={l.unitPrice} />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </>
        )}
      </DetailDrawer>
    </Stack>
  );
}
