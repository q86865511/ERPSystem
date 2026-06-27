import { useState } from 'react';
import {
  Badge,
  Button,
  Drawer,
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
import { MoneyText } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
import { useItemMap, usePartnerMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateInvoice, useInvoice, useInvoices, useOrder, useOrders } from './api';

const INVOICEABLE = new Set(['CONFIRMED', 'PARTIALLY_SHIPPED', 'SHIPPED']);
const orDefault = (value: string | undefined, fallback: string) => value ?? fallback;

export function SalesInvoicesPanel() {
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
      notifyError('Pick a sales order');
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
      notifyError('Nothing shipped to invoice on this SO');
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
      notifySuccess(`Invoice ${inv?.invoiceNumber} posted`);
      close();
      if (inv?.id != null) setDetailId(inv.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = invoices.data ?? [];
  return (
    <Stack>
      {canDo('sales.invoice') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            New invoice
          </Button>
        </Group>
      )}

      <Table.ScrollContainer minWidth={760}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Invoice #</Table.Th>
              <Table.Th>Customer</Table.Th>
              <Table.Th ta="right">Gross</Table.Th>
              <Table.Th ta="right">COGS</Table.Th>
              <Table.Th ta="right">Open</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((b) => (
              <Table.Tr key={b.id}>
                <Table.Td>{b.invoiceNumber}</Table.Td>
                <Table.Td>{b.partnerId != null ? (partners.get(b.partnerId) ?? b.partnerId) : '—'}</Table.Td>
                <Table.Td ta="right">
                  <MoneyText value={b.grossAmount} />
                </Table.Td>
                <Table.Td ta="right">
                  <MoneyText value={b.cogsAmount} />
                </Table.Td>
                <Table.Td ta="right">
                  <MoneyText value={b.openBalance} />
                </Table.Td>
                <Table.Td>
                  <StatusBadge status={b.status} />
                </Table.Td>
                <Table.Td ta="right">
                  <Button size="xs" variant="subtle" onClick={() => setDetailId(b.id ?? null)}>
                    View
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {!invoices.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No invoices yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New customer invoice" size="xl">
        <Stack>
          <Group grow>
            <Select
              label="Sales order"
              placeholder="SO to invoice"
              searchable
              data={soOptions}
              value={soId != null ? String(soId) : null}
              onChange={(v) => {
                setSoId(v ? Number(v) : null);
                setOverrides({});
              }}
            />
            <TextInput
              label="Tax rate code"
              value={taxRateCode}
              onChange={(e) => setTaxRateCode(e.currentTarget.value)}
            />
            <DateInput label="Posting date" value={postingDate} onChange={setPostingDate} />
          </Group>

          {soId && so.isLoading && <Loader size="sm" />}
          {soId && so.data && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Shipped</Table.Th>
                  <Table.Th ta="right">Invoiced</Table.Th>
                  <Table.Th ta="right" w={110}>
                    Qty
                  </Table.Th>
                  <Table.Th ta="right" w={120}>
                    Unit price
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
              Cancel
            </Button>
            <Button onClick={submit} loading={create.isPending}>
              Post invoice
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={`Invoice ${detail.data?.invoiceNumber ?? ''}`}
      >
        {detail.data && (
          <Stack>
            <Group>
              <StatusBadge status={detail.data.status} />
              {detail.data.journalEntryId != null && (
                <Badge variant="light">JE #{detail.data.journalEntryId}</Badge>
              )}
            </Group>
            <SimpleGrid cols={4}>
              <Text size="sm">
                Goods <MoneyText value={detail.data.goodsAmount} />
              </Text>
              <Text size="sm">
                VAT <MoneyText value={detail.data.vatAmount} />
              </Text>
              <Text size="sm">
                Gross <MoneyText value={detail.data.grossAmount} />
              </Text>
              <Text size="sm">
                COGS <MoneyText value={detail.data.cogsAmount} />
              </Text>
            </SimpleGrid>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Qty</Table.Th>
                  <Table.Th ta="right">Net</Table.Th>
                  <Table.Th ta="right">VAT</Table.Th>
                  <Table.Th ta="right">COGS</Table.Th>
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
              Posted Dr AR / Cr Revenue + Output VAT, and Dr COGS / Cr Deferred-COGS.
            </Text>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
