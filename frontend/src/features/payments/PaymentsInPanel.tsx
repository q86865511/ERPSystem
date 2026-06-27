import { useState } from 'react';
import { Badge, Button, Drawer, Group, Modal, Stack, Table, Text, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { PayInRequest } from '../../api/types';
import { PartnerSelect } from '../../components/EntitySelect';
import { MoneyText, formatMoney, sumMoney } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
import { usePartnerMap } from '../masterdata/api';
import { useInvoices } from '../sales/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { usePayIn, usePayment, usePayments } from './api';

export function PaymentsInPanel() {
  const { canDo } = useAuth();
  const payments = usePayments('IN');
  const invoices = useInvoices();
  const partners = usePartnerMap();
  const pay = usePayIn();
  const [opened, { open, close }] = useDisclosure(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = usePayment(detailId);

  const [partnerId, setPartnerId] = useState<number | null>(null);
  const [postingDate, setPostingDate] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const [allocs, setAllocs] = useState<Record<number, string>>({});

  const openForm = () => {
    setPartnerId(null);
    setAllocs({});
    open();
  };

  const openInvoices = (invoices.data ?? []).filter(
    (i) => i.partnerId === partnerId && i.id != null && Number(i.openBalance ?? 0) > 0,
  );
  const total = sumMoney(Object.values(allocs));

  const submit = async () => {
    const allocations = Object.entries(allocs)
      .filter(([, a]) => a && Number(a) > 0)
      .map(([invoiceId, amount]) => ({ invoiceId: Number(invoiceId), amount }));
    if (!partnerId || allocations.length === 0) {
      notifyError('Pick a customer and allocate to at least one invoice');
      return;
    }
    const body: PayInRequest = {
      partnerId,
      amount: sumMoney(allocations.map((a) => a.amount)),
      postingDate: postingDate ?? undefined,
      allocations,
    };
    try {
      const p = await pay.mutateAsync(body);
      notifySuccess(`Receipt ${p?.payNumber} posted`);
      close();
      if (p?.id != null) setDetailId(p.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = payments.data ?? [];
  return (
    <Stack>
      {canDo('payments.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            New receipt
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Receipt #</Table.Th>
            <Table.Th>Customer</Table.Th>
            <Table.Th ta="right">Amount</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((p) => (
            <Table.Tr key={p.id}>
              <Table.Td>{p.payNumber}</Table.Td>
              <Table.Td>{p.partnerId != null ? (partners.get(p.partnerId) ?? p.partnerId) : '—'}</Table.Td>
              <Table.Td ta="right">
                <MoneyText value={p.amount} />
              </Table.Td>
              <Table.Td>
                <StatusBadge status={p.status} />
              </Table.Td>
              <Table.Td ta="right">
                <Button size="xs" variant="subtle" onClick={() => setDetailId(p.id ?? null)}>
                  View
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!payments.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No receipts yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New customer receipt" size="lg">
        <Stack>
          <Group grow>
            <PartnerSelect
              label="Customer"
              customer
              value={partnerId}
              onChange={(id) => {
                setPartnerId(id);
                setAllocs({});
              }}
            />
            <DateInput label="Posting date" value={postingDate} onChange={setPostingDate} />
          </Group>

          {partnerId && (
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Invoice</Table.Th>
                  <Table.Th ta="right">Open</Table.Th>
                  <Table.Th ta="right" w={140}>
                    Allocate
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {openInvoices.map((i) => (
                  <Table.Tr key={i.id}>
                    <Table.Td>{i.invoiceNumber}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={i.openBalance} />
                    </Table.Td>
                    <Table.Td>
                      <TextInput
                        size="xs"
                        placeholder={i.openBalance}
                        value={i.id != null ? (allocs[i.id] ?? '') : ''}
                        onChange={(e) =>
                          i.id != null &&
                          setAllocs((a) => ({ ...a, [i.id as number]: e.currentTarget.value }))
                        }
                      />
                    </Table.Td>
                  </Table.Tr>
                ))}
                {openInvoices.length === 0 && (
                  <Table.Tr>
                    <Table.Td colSpan={3}>
                      <Text c="dimmed" size="sm">
                        No open invoices for this customer.
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                )}
              </Table.Tbody>
            </Table>
          )}

          <Group justify="space-between">
            <Text fw={600}>Total: {formatMoney(total)}</Text>
            <Group>
              <Button variant="default" onClick={close}>
                Cancel
              </Button>
              <Button onClick={submit} loading={pay.isPending}>
                Post receipt
              </Button>
            </Group>
          </Group>
        </Stack>
      </Modal>

      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="md"
        title={`Receipt ${detail.data?.payNumber ?? ''}`}
      >
        {detail.data && (
          <Stack>
            <Group>
              <StatusBadge status={detail.data.status} />
              {detail.data.journalEntryId != null && (
                <Badge variant="light">JE #{detail.data.journalEntryId}</Badge>
              )}
            </Group>
            <Text>
              Amount <MoneyText value={detail.data.amount} />
            </Text>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Document #</Table.Th>
                  <Table.Th ta="right">Allocated</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(detail.data.allocations ?? []).map((a) => (
                  <Table.Tr key={a.id}>
                    <Table.Td>{a.documentId}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={a.amount} />
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Text size="xs" c="dimmed">
              Posted Dr Cash / Cr AR and settled the allocated invoices.
            </Text>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
