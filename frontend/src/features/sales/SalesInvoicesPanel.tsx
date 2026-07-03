import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import type { CreateInvoiceRequest } from '../../api/types';
import { DataTable, DetailDrawer, MoneyText, StatusBadge } from '../../components';
import type { DataTableColumn } from '../../components';
import { useI18n } from '../../i18n';
import { useItemMap, usePartnerMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateInvoice, useInvoice, useInvoices, useOrder, useOrders } from './api';

const INVOICEABLE = new Set(['CONFIRMED', 'PARTIALLY_SHIPPED', 'SHIPPED']);
const orDefault = (value: string | undefined, fallback: string) => value ?? fallback;

export function SalesInvoicesPanel() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const { canDo } = useAuth();
  const invoices = useInvoices();
  const orders = useOrders();
  const items = useItemMap();
  const partners = usePartnerMap();
  const create = useCreateInvoice();
  const [opened, { open, close }] = useDisclosure(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useInvoice(detailId);

  const [soId, setSoId] = useState<number | null>(null);
  const [taxRateCode, setTaxRateCode] = useState('STANDARD');
  const [postingDate, setPostingDate] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const [overrides, setOverrides] = useState<Record<number, { qty?: string; unitPrice?: string }>>({});
  const so = useOrder(soId);

  const openForm = () => {
    setSoId(null);
    setOverrides({});
    setTaxRateCode('STANDARD');
    open();
  };

  const soOptions = (orders.data ?? [])
    .filter((o) => o.status && INVOICEABLE.has(o.status))
    .map((o) => ({ value: String(o.id), label: o.soNumber ?? String(o.id) }));

  const defaultQty = (l: { qtyShipped?: string; qtyInvoiced?: string }) =>
    Math.max(0, Number(l.qtyShipped ?? 0) - Number(l.qtyInvoiced ?? 0));

  const submit = async () => {
    if (!soId || !so.data) {
      notifyError(t('sales.invoice.pickSo'));
      return;
    }
    const lines = (so.data.lines ?? [])
      .map((l) => {
        const id = l.id as number;
        return {
          soLineId: id,
          qty: orDefault(overrides[id]?.qty, String(defaultQty(l))),
          unitPrice: orDefault(overrides[id]?.unitPrice, l.unitPrice ?? '0'),
        };
      })
      .filter((l) => Number(l.qty) > 0);
    if (lines.length === 0) {
      notifyError(t('sales.invoice.nothingToInvoice'));
      return;
    }
    const body: CreateInvoiceRequest = {
      salesOrderId: soId,
      taxRateCode,
      postingDate: postingDate ?? undefined,
      lines,
    };
    try {
      const inv = await create.mutateAsync(body);
      notifySuccess(t('sales.invoice.posted', { invoiceNumber: inv?.invoiceNumber ?? '' }));
      close();
      if (inv?.id != null) setDetailId(inv.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = invoices.data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'invoiceNumber', label: t('sales.invoice.invoiceNumber') },
    {
      key: 'customer',
      label: t('field.customer'),
      render: (b) => (b.partnerId != null ? (partners.get(b.partnerId) ?? b.partnerId) : '—'),
    },
    {
      key: 'gross',
      label: t('field.gross'),
      align: 'right',
      render: (b) => <MoneyText value={b.grossAmount} />,
    },
    {
      key: 'cogs',
      label: t('sales.invoice.cogs'),
      align: 'right',
      render: (b) => <MoneyText value={b.cogsAmount} />,
    },
    {
      key: 'open',
      label: t('sales.invoice.open'),
      align: 'right',
      render: (b) => <MoneyText value={b.openBalance} />,
    },
    { key: 'status', label: t('field.status'), render: (b) => <StatusBadge status={b.status} /> },
  ];

  return (
    <Stack>
      {canDo('sales.invoice') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            {t('sales.invoice.new')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(b) => b.id ?? b.invoiceNumber ?? ''}
        isLoading={invoices.isLoading}
        emptyMessage={t('sales.invoice.empty')}
        onRowClick={(b) => setDetailId(b.id ?? null)}
        minWidth={760}
      />

      <Modal opened={opened} onClose={close} title={t('sales.invoice.modalTitle')} size="xl">
        <Stack>
          <Group grow>
            <Select
              label={t('sales.invoice.salesOrder')}
              placeholder={t('sales.invoice.soPlaceholder')}
              searchable
              data={soOptions}
              value={soId != null ? String(soId) : null}
              onChange={(v) => {
                setSoId(v ? Number(v) : null);
                setOverrides({});
              }}
            />
            <TextInput
              label={t('sales.invoice.taxRateCode')}
              value={taxRateCode}
              onChange={(e) => setTaxRateCode(e.currentTarget.value)}
            />
            <DateInput label={t('sales.invoice.postingDate')} value={postingDate} onChange={setPostingDate} />
          </Group>

          {soId && so.isLoading && <Loader size="sm" />}
          {soId && so.data && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('sales.invoice.shipped')}</Table.Th>
                  <Table.Th ta="right">{t('sales.invoice.invoiced')}</Table.Th>
                  <Table.Th ta="right" w={110}>
                    {t('sales.invoice.qty')}
                  </Table.Th>
                  <Table.Th ta="right" w={120}>
                    {t('field.unitPrice')}
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(so.data.lines ?? []).map((l) => {
                  const id = l.id as number;
                  return (
                    <Table.Tr key={id}>
                      <Table.Td>{l.itemId != null ? (items.get(l.itemId) ?? l.itemId) : '—'}</Table.Td>
                      <Table.Td ta="right">{l.qtyShipped}</Table.Td>
                      <Table.Td ta="right">{l.qtyInvoiced}</Table.Td>
                      <Table.Td>
                        <TextInput
                          size="xs"
                          value={orDefault(overrides[id]?.qty, String(defaultQty(l)))}
                          onChange={(e) =>
                            setOverrides((o) => ({ ...o, [id]: { ...o[id], qty: e.currentTarget.value } }))
                          }
                        />
                      </Table.Td>
                      <Table.Td>
                        <TextInput
                          size="xs"
                          value={orDefault(overrides[id]?.unitPrice, l.unitPrice ?? '0')}
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
              {t('sales.invoice.post')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <DetailDrawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        title={t('sales.invoice.drawerTitle', { invoiceNumber: detail.data?.invoiceNumber ?? '' })}
        footer={
          <Group justify="flex-end">
            <Button
              variant="default"
              size="xs"
              onClick={() => detailId && navigate(`/print/sales-invoice/${detailId}`)}
            >
              {t('print.print')}
            </Button>
          </Group>
        }
      >
        {detail.data && (
          <>
            <Group>
              <StatusBadge status={detail.data.status} size="md" />
              {detail.data.journalEntryId != null && (
                <Badge variant="light">{t('sales.je', { id: detail.data.journalEntryId })}</Badge>
              )}
            </Group>
            <SimpleGrid cols={4}>
              <Text size="sm">
                {t('sales.invoice.goods')} <MoneyText value={detail.data.goodsAmount} />
              </Text>
              <Text size="sm">
                {t('field.vat')} <MoneyText value={detail.data.vatAmount} />
              </Text>
              <Text size="sm">
                {t('field.gross')} <MoneyText value={detail.data.grossAmount} />
              </Text>
              <Text size="sm">
                {t('sales.invoice.cogs')} <MoneyText value={detail.data.cogsAmount} />
              </Text>
            </SimpleGrid>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('field.item')}</Table.Th>
                  <Table.Th ta="right">{t('sales.invoice.qty')}</Table.Th>
                  <Table.Th ta="right">{t('field.net')}</Table.Th>
                  <Table.Th ta="right">{t('field.vat')}</Table.Th>
                  <Table.Th ta="right">{t('sales.invoice.cogs')}</Table.Th>
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
                    <Table.Td ta="right">
                      <MoneyText value={l.lineCogs} />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text size="xs" c="dimmed">
              {t('sales.invoice.glNote')}
            </Text>
          </>
        )}
      </DetailDrawer>
    </Stack>
  );
}
