import { useState } from 'react';
import {
  Badge,
  Button,
  Group,
  Loader,
  Modal,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { CreateBillRequest } from '../../api/types';
import { DataTable, DetailDrawer, MoneyText, StatusBadge } from '../../components';
import type { DataTableColumn } from '../../components';
import { useItemMap, usePartnerMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useBill, useBills, useCreateBill, useOrder, useOrders } from './api';

const BILLABLE_STATUS = new Set(['CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED']);

function field(value: string | undefined, fallback: string) {
  return value ?? fallback;
}

export function VendorBillsPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const bills = useBills();
  const orders = useOrders();
  const items = useItemMap();
  const partners = usePartnerMap();
  const create = useCreateBill();
  const [opened, { open, close }] = useDisclosure(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useBill(detailId);

  const [poId, setPoId] = useState<number | null>(null);
  const [taxRateCode, setTaxRateCode] = useState('STANDARD');
  const [postingDate, setPostingDate] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const [overrides, setOverrides] = useState<Record<number, { qty?: string; unitPrice?: string }>>({});
  const po = useOrder(poId);

  const openForm = () => {
    setPoId(null);
    setOverrides({});
    setTaxRateCode('STANDARD');
    open();
  };

  const poOptions = (orders.data ?? [])
    .filter((o) => o.status && BILLABLE_STATUS.has(o.status))
    .map((o) => ({ value: String(o.id), label: o.poNumber ?? String(o.id) }));

  const defaultQty = (l: { qtyReceived?: string; qtyBilled?: string }) =>
    Math.max(0, Number(l.qtyReceived ?? 0) - Number(l.qtyBilled ?? 0));

  const submit = async () => {
    if (!poId || !po.data) {
      notifyError(t('purchasing.bill.pickPo'));
      return;
    }
    const lines = (po.data.lines ?? [])
      .map((l) => {
        const id = l.id as number;
        const qty = field(overrides[id]?.qty, String(defaultQty(l)));
        const unitPrice = field(overrides[id]?.unitPrice, l.unitPrice ?? '0');
        return { poLineId: id, qty, unitPrice };
      })
      .filter((l) => Number(l.qty) > 0);
    if (lines.length === 0) {
      notifyError(t('purchasing.bill.nothingToBill'));
      return;
    }
    const body: CreateBillRequest = {
      purchaseOrderId: poId,
      taxRateCode,
      postingDate: postingDate ?? undefined,
      lines,
    };
    try {
      const bill = await create.mutateAsync(body);
      notifySuccess(t('purchasing.bill.posted', { billNumber: bill?.billNumber ?? '' }));
      close();
      if (bill?.id != null) setDetailId(bill.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = bills.data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'billNumber', label: t('purchasing.bill.billNumber') },
    {
      key: 'vendor',
      label: t('field.vendor'),
      render: (b) => (b.partnerId != null ? (partners.get(b.partnerId) ?? b.partnerId) : '—'),
    },
    {
      key: 'gross',
      label: t('field.gross'),
      align: 'right',
      render: (b) => <MoneyText value={b.grossAmount} />,
    },
    {
      key: 'open',
      label: t('purchasing.bill.open'),
      align: 'right',
      render: (b) => <MoneyText value={b.openBalance} />,
    },
    { key: 'status', label: t('field.status'), render: (b) => <StatusBadge status={b.status} /> },
  ];

  return (
    <Stack>
      {canDo('purchasing.vendorBill') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            {t('purchasing.bill.new')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(b) => b.id ?? b.billNumber ?? ''}
        isLoading={bills.isLoading}
        emptyMessage={t('purchasing.bill.none')}
        onRowClick={(b) => setDetailId(b.id ?? null)}
        minWidth={720}
      />

      <Modal opened={opened} onClose={close} title={t('purchasing.bill.new')} size="xl">
        <Stack>
          <Group grow>
            <Select
              label={t('purchasing.bill.purchaseOrder')}
              placeholder={t('purchasing.bill.poToBillPlaceholder')}
              searchable
              data={poOptions}
              value={poId != null ? String(poId) : null}
              onChange={(v) => {
                setPoId(v ? Number(v) : null);
                setOverrides({});
              }}
            />
            <TextInput
              label={t('purchasing.bill.taxRateCode')}
              value={taxRateCode}
              onChange={(e) => setTaxRateCode(e.currentTarget.value)}
            />
            <DateInput label={t('purchasing.bill.postingDate')} value={postingDate} onChange={setPostingDate} />
          </Group>

          {poId && po.isLoading && <Loader size="sm" />}
          {poId && po.data && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('purchasing.bill.received')}</Table.Th>
                  <Table.Th ta="right">{t('purchasing.bill.billed')}</Table.Th>
                  <Table.Th ta="right" w={110}>
                    {t('field.quantity')}
                  </Table.Th>
                  <Table.Th ta="right" w={120}>
                    {t('field.unitPrice')}
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(po.data.lines ?? []).map((l) => {
                  const id = l.id as number;
                  return (
                    <Table.Tr key={id}>
                      <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                      <Table.Td ta="right">{l.qtyReceived}</Table.Td>
                      <Table.Td ta="right">{l.qtyBilled}</Table.Td>
                      <Table.Td>
                        <TextInput
                          size="xs"
                          value={field(overrides[id]?.qty, String(defaultQty(l)))}
                          onChange={(e) =>
                            setOverrides((o) => ({ ...o, [id]: { ...o[id], qty: e.currentTarget.value } }))
                          }
                        />
                      </Table.Td>
                      <Table.Td>
                        <TextInput
                          size="xs"
                          value={field(overrides[id]?.unitPrice, l.unitPrice ?? '0')}
                          onChange={(e) =>
                            setOverrides((o) => ({
                              ...o,
                              [id]: { ...o[id], unitPrice: e.currentTarget.value },
                            }))
                          }
                        />
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </Table.Tbody>
            </Table>
          )}

          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button onClick={submit} loading={create.isPending}>
              {t('purchasing.bill.postBill')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <DetailDrawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        title={t('purchasing.bill.drawerTitle', { billNumber: detail.data?.billNumber ?? '' })}
      >
        {detail.data && (
          <>
            <Group>
              <StatusBadge status={detail.data.status} />
              {detail.data.journalEntryId != null && (
                <Badge variant="light">
                  {t('purchasing.bill.journalEntry', { id: detail.data.journalEntryId })}
                </Badge>
              )}
            </Group>
            <SimpleGrid cols={3}>
              <Text size="sm">
                {t('purchasing.bill.goods')} <MoneyText value={detail.data.goodsAmount} />
              </Text>
              <Text size="sm">
                {t('field.vat')} <MoneyText value={detail.data.vatAmount} />
              </Text>
              <Text size="sm">
                {t('field.gross')} <MoneyText value={detail.data.grossAmount} />
              </Text>
            </SimpleGrid>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('field.quantity')}</Table.Th>
                  <Table.Th ta="right">{t('field.net')}</Table.Th>
                  <Table.Th ta="right">{t('field.vat')}</Table.Th>
                  <Table.Th>{t('purchasing.bill.match')}</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(detail.data.lines ?? []).map((l) => (
                  <Table.Tr key={l.id}>
                    <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                    <Table.Td ta="right">{l.qty}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={l.lineNet} />
                    </Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={l.lineVat} />
                    </Table.Td>
                    <Table.Td>
                      <StatusBadge status={l.matchStatus} />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text size="xs" c="dimmed">
              {t('purchasing.bill.postingNote')}
            </Text>
          </>
        )}
      </DetailDrawer>
    </Stack>
  );
}
