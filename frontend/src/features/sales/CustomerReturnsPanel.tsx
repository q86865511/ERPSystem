import { useState } from 'react';
import { Badge, Button, Drawer, Group, Modal, Select, SimpleGrid, Stack, Table, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { CreateReturnRequest } from '../../api/types';
import { LocationSelect } from '../../components/EntitySelect';
import { MoneyText } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
import { useItemMap, usePartnerMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateReturn, useInvoices, useReturn, useReturns } from './api';

export function CustomerReturnsPanel() {
  const { canDo } = useAuth();
  const returns = useReturns();
  const invoices = useInvoices();
  const items = useItemMap();
  const partners = usePartnerMap();
  const create = useCreateReturn();
  const [opened, { open, close }] = useDisclosure(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const detail = useReturn(detailId);

  const [invoiceId, setInvoiceId] = useState<number | null>(null);
  const [locationId, setLocationId] = useState<number | null>(null);
  const [postingDate, setPostingDate] = useState<string | null>(dayjs().format('YYYY-MM-DD'));

  const openForm = () => {
    setInvoiceId(null);
    setLocationId(null);
    open();
  };

  // Only an as-yet-unpaid posted invoice can be returned in full.
  const invoiceOptions = (invoices.data ?? [])
    .filter((i) => i.status === 'POSTED')
    .map((i) => ({ value: String(i.id), label: i.invoiceNumber ?? String(i.id) }));

  const submit = async () => {
    if (!invoiceId || !locationId) {
      notifyError('Pick an invoice and a return-to location');
      return;
    }
    const body: CreateReturnRequest = {
      salesInvoiceId: invoiceId,
      stockLocationId: locationId,
      postingDate: postingDate ?? undefined,
    };
    try {
      const r = await create.mutateAsync(body);
      notifySuccess(`Return ${r?.returnNumber} posted`);
      close();
      if (r?.id != null) setDetailId(r.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = returns.data ?? [];
  return (
    <Stack>
      {canDo('sales.customerReturn') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            New return
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Return #</Table.Th>
            <Table.Th>Customer</Table.Th>
            <Table.Th ta="right">Gross</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((r) => (
            <Table.Tr key={r.id}>
              <Table.Td>{r.returnNumber}</Table.Td>
              <Table.Td>{r.partnerId != null ? (partners.get(r.partnerId) ?? r.partnerId) : '—'}</Table.Td>
              <Table.Td ta="right">
                <MoneyText value={r.grossAmount} />
              </Table.Td>
              <Table.Td>
                <StatusBadge status={r.status} />
              </Table.Td>
              <Table.Td ta="right">
                <Button size="xs" variant="subtle" onClick={() => setDetailId(r.id ?? null)}>
                  View
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!returns.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No customer returns yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New customer return" size="md">
        <Stack>
          <Select
            label="Invoice to return"
            placeholder="Posted, unpaid invoice"
            searchable
            data={invoiceOptions}
            value={invoiceId != null ? String(invoiceId) : null}
            onChange={(v) => setInvoiceId(v ? Number(v) : null)}
          />
          <LocationSelect
            label="Return to location"
            locationType="STOCK"
            value={locationId}
            onChange={setLocationId}
          />
          <DateInput label="Posting date" value={postingDate} onChange={setPostingDate} />
          <Text size="xs" c="dimmed">
            Posts a credit note that reverses the whole invoice (revenue, VAT, COGS) and returns the
            goods to stock.
          </Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              Cancel
            </Button>
            <Button onClick={submit} loading={create.isPending} color="grape">
              Post return
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Drawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        position="right"
        size="xl"
        title={`Customer return ${detail.data?.returnNumber ?? ''}`}
      >
        {detail.data && (
          <Stack>
            <Group>
              <StatusBadge status={detail.data.status} />
              {detail.data.creditNoteJournalEntryId != null && (
                <Badge variant="light">Credit note JE #{detail.data.creditNoteJournalEntryId}</Badge>
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
                COGS <MoneyText value={detail.data.cogsAmount} />
              </Text>
            </SimpleGrid>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Item</Table.Th>
                  <Table.Th ta="right">Qty</Table.Th>
                  <Table.Th ta="right">Net</Table.Th>
                  <Table.Th ta="right">COGS</Table.Th>
                  <Table.Th>Stock JE</Table.Th>
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
                      <MoneyText value={l.lineCogs} />
                    </Table.Td>
                    <Table.Td>
                      {l.stockJournalEntryId != null && (
                        <Badge variant="light" size="sm">
                          JE #{l.stockJournalEntryId}
                        </Badge>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
