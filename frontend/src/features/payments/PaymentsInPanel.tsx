import { useState } from 'react';
import { Badge, Button, Group, Modal, Stack, Table, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { PayInRequest } from '../../api/types';
import { PartnerSelect } from '../../components/EntitySelect';
import { sumMoney } from '../../components/Money';
import { AmountAllocationTable, DataTable, DetailDrawer, MoneyText, StatusBadge } from '../../components';
import type { DataTableColumn } from '../../components';
import { usePartnerMap } from '../masterdata/api';
import { useInvoices } from '../sales/api';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { usePayIn, usePayment, usePayments } from './api';

export function PaymentsInPanel() {
  const { t } = useI18n();
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

  const submit = async () => {
    const allocations = Object.entries(allocs)
      .filter(([, a]) => a && Number(a) > 0)
      .map(([invoiceId, amount]) => ({ invoiceId: Number(invoiceId), amount }));
    if (!partnerId || allocations.length === 0) {
      notifyError(t('payments.in.pickCustomer'));
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
      notifySuccess(t('payments.in.posted', { payNumber: p?.payNumber ?? '' }));
      close();
      if (p?.id != null) setDetailId(p.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = payments.data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'receiptNumber', label: t('payments.in.receiptNumber'), render: (p) => p.payNumber },
    {
      key: 'customer',
      label: t('field.customer'),
      render: (p) => (p.partnerId != null ? (partners.get(p.partnerId) ?? p.partnerId) : '—'),
    },
    { key: 'amount', label: t('field.amount'), align: 'right', render: (p) => <MoneyText value={p.amount} /> },
    { key: 'status', label: t('field.status'), render: (p) => <StatusBadge status={p.status} /> },
  ];

  return (
    <Stack>
      {canDo('payments.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            {t('payments.in.newReceipt')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(p) => p.id ?? p.payNumber ?? ''}
        isLoading={payments.isLoading}
        emptyMessage={t('payments.in.noReceipts')}
        onRowClick={(p) => setDetailId(p.id ?? null)}
      />

      <Modal opened={opened} onClose={close} title={t('payments.in.newCustomerReceipt')} size="lg">
        <Stack>
          <Group grow>
            <PartnerSelect
              label={t('field.customer')}
              customer
              value={partnerId}
              onChange={(id) => {
                setPartnerId(id);
                setAllocs({});
              }}
            />
            <DateInput label={t('payments.postingDate')} value={postingDate} onChange={setPostingDate} />
          </Group>

          {partnerId && (
            <AmountAllocationTable
              rows={openInvoices.map((i) => ({
                id: i.id as number,
                openBalance: i.openBalance,
                label: i.invoiceNumber ?? String(i.id),
              }))}
              allocs={allocs}
              onChange={setAllocs}
              documentLabel={t('payments.in.invoice')}
              amountLabel={t('payments.allocate')}
              totalLabel={t('payments.totalLabel', { amount: '' })}
              emptyMessage={t('payments.in.noOpenInvoices')}
            />
          )}

          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button onClick={submit} loading={pay.isPending}>
              {t('payments.in.postReceipt')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <DetailDrawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        title={t('payments.in.drawerTitle', { payNumber: detail.data?.payNumber ?? '' })}
        size="md"
      >
        {detail.data && (
          <>
            <Group>
              <StatusBadge status={detail.data.status} />
              {detail.data.journalEntryId != null && (
                <Badge variant="light">{t('payments.je', { id: detail.data.journalEntryId })}</Badge>
              )}
            </Group>
            <Text>
              {t('field.amount')} <MoneyText value={detail.data.amount} />
            </Text>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('payments.documentNumber')}</Table.Th>
                  <Table.Th ta="right">{t('payments.allocated')}</Table.Th>
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
              {t('payments.in.postingNote')}
            </Text>
          </>
        )}
      </DetailDrawer>
    </Stack>
  );
}
