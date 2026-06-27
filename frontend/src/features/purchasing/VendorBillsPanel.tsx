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
import type { CreateBillRequest } from '../../api/types';
import { MoneyText } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
import { useItemMap, usePartnerMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useBill, useBills, useCreateBill, useOrder, useOrders } from './api';

const BILLABLE_STATUS = new Set(['CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED']);

function field(value: string | undefined, fallback: string) {
  return value ?? fallback;
}

export function VendorBillsPanel() {
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
      notifyError('Pick a purchase order');
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
      notifyError('Nothing to bill on this PO');
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
      notifySuccess(`Vendor bill ${bill?.billNumber} posted`);
      close();
      if (bill?.id != null) setDetailId(bill.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = bills.data ?? [];
  return (
    <Stack>
      {canDo('purchasing.vendorBill') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            New vendor bill
          </Button>
        </Group>
      )}

      <Table.ScrollContainer minWidth={720}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Bill #</Table.Th>
              <Table.Th>Vendor</Table.Th>
              <Table.Th ta="right">Gross</Table.Th>
              <Table.Th ta="right">Open</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((b) => (
              <Table.Tr key={b.id}>
                <Table.Td>{b.billNumber}</Table.Td>
                <Table.Td>{b.partnerId != null ? (partners.get(b.partnerId) ?? b.partnerId) : '—'}</Table.Td>
                <Table.Td ta="right">
                  <MoneyText value={b.grossAmount} />
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
      {!bills.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No vendor bills yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New vendor bill" size="xl">
        <Stack>
          <Group grow>
            <Select
              label="Purchase order"
              placeholder="PO to bill"
              searchable
              data={poOptions}
              value={poId != null ? String(poId) : null}
              onChange={(v) => {
                setPoId(v ? Number(v) : null);
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

          {poId && po.isLoading && <Loader size="sm" />}
          {poId && po.data && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Received</Table.Th>
                  <Table.Th ta="right">Billed</Table.Th>
                  <Table.Th ta="right" w={110}>
                    Qty
                  </Table.Th>
                  <Table.Th ta="right" w={120}>
                    Unit price
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
              Cancel
            </Button>
            <Button onClick={submit} loading={create.isPending}>
              Post bill
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={`Vendor bill ${detail.data?.billNumber ?? ''}`}
      >
        {detail.data && (
          <Stack>
            <Group>
              <StatusBadge status={detail.data.status} />
              {detail.data.journalEntryId != null && (
                <Badge variant="light">JE #{detail.data.journalEntryId}</Badge>
              )}
            </Group>
            <SimpleGrid cols={3}>
              <Text size="sm">
                Goods <MoneyText value={detail.data.goodsAmount} />
              </Text>
              <Text size="sm">
                VAT <MoneyText value={detail.data.vatAmount} />
              </Text>
              <Text size="sm">
                Gross <MoneyText value={detail.data.grossAmount} />
              </Text>
            </SimpleGrid>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Qty</Table.Th>
                  <Table.Th ta="right">Net</Table.Th>
                  <Table.Th ta="right">VAT</Table.Th>
                  <Table.Th>Match</Table.Th>
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
              Posted Dr GR-IR + Dr Input VAT / Cr AP; any price variance revalues inventory.
            </Text>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
