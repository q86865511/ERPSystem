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
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useConfirmOrder, useCreateOrder, useOrder, useOrders } from './api';

type LineForm = { itemId: number | null; qtyOrdered: string; unitPrice: string };

function emptyLine(): LineForm {
  return { itemId: null, qtyOrdered: '', unitPrice: '' };
}

export function PurchaseOrdersPanel() {
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
    validate: {
      partnerId: (v) => (v != null ? null : t('purchasing.po.pickVendor')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    const lines = v.lines.filter((l) => l.itemId != null && l.qtyOrdered && l.unitPrice);
    if (lines.length === 0) {
      notifyError(t('purchasing.po.lineRequired'));
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
      notifySuccess(t('purchasing.po.created', { poNumber: po?.poNumber ?? '' }));
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
      notifySuccess(t('purchasing.po.confirmed'));
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
            {t('purchasing.po.new')}
          </Button>
        </Group>
      )}

      <Table.ScrollContainer minWidth={620}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('purchasing.po.poNumber')}</Table.Th>
              <Table.Th>{t('field.vendor')}</Table.Th>
              <Table.Th>{t('purchasing.po.orderDate')}</Table.Th>
              <Table.Th>{t('field.status')}</Table.Th>
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
                    {t('common.view')}
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {!isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          {t('purchasing.po.none')}
        </Text>
      )}

      {/* Create modal */}
      <Modal opened={opened} onClose={close} title={t('purchasing.po.new')} size="xl">
        <form onSubmit={submit}>
          <Stack>
            <Group grow>
              <PartnerSelect
                label={t('field.vendor')}
                vendor
                required
                value={form.values.partnerId}
                onChange={(id) => form.setFieldValue('partnerId', id)}
                error={form.errors.partnerId}
              />
              <DateInput label={t('purchasing.po.orderDate')} value={form.values.orderDate} onChange={(d) => form.setFieldValue('orderDate', d)} />
            </Group>

            <Text fw={600} size="sm">
              {t('purchasing.po.lines')}
            </Text>
            {form.values.lines.map((_, i) => (
              <Group key={i} align="flex-end" wrap="nowrap">
                <ItemSelect
                  label={i === 0 ? t('field.item') : undefined}
                  value={form.values.lines[i]?.itemId ?? null}
                  onChange={(id) => form.setFieldValue(`lines.${i}.itemId`, id)}
                  style={{ flex: 1 }}
                />
                <TextInput
                  label={i === 0 ? t('purchasing.po.qty') : undefined}
                  w={100}
                  {...form.getInputProps(`lines.${i}.qtyOrdered`)}
                />
                <TextInput
                  label={i === 0 ? t('field.unitPrice') : undefined}
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

      {/* Detail drawer */}
      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={t('purchasing.po.drawerTitle', { poNumber: detail.data?.poNumber ?? '' })}
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
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('purchasing.po.ordered')}</Table.Th>
                  <Table.Th ta="right">{t('purchasing.po.received')}</Table.Th>
                  <Table.Th ta="right">{t('purchasing.po.billed')}</Table.Th>
                  <Table.Th ta="right">{t('field.unitPrice')}</Table.Th>
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
                  {t('purchasing.po.confirmOrder')}
                </Button>
              </Group>
            )}
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
