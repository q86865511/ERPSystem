import { useState } from 'react';
import { Badge, Button, Group, Modal, Stack, Table, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { PayOutRequest } from '../../api/types';
import { PartnerSelect } from '../../components/EntitySelect';
import { sumMoney } from '../../components/Money';
import { AmountAllocationTable, DataTable, DetailDrawer, MoneyText, StatusBadge } from '../../components';
import type { DataTableColumn } from '../../components';
import { usePartnerMap } from '../masterdata/api';
import { useBills } from '../purchasing/api';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { usePayment, usePayments, usePayOut } from './api';

export function PaymentsOutPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const payments = usePayments('OUT');
  const bills = useBills();
  const partners = usePartnerMap();
  const pay = usePayOut();
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

  const openBills = (bills.data ?? []).filter(
    (b) => b.partnerId === partnerId && b.id != null && Number(b.openBalance ?? 0) > 0,
  );

  const submit = async () => {
    const allocations = Object.entries(allocs)
      .filter(([, a]) => a && Number(a) > 0)
      .map(([billId, amount]) => ({ billId: Number(billId), amount }));
    if (!partnerId || allocations.length === 0) {
      notifyError(t('payments.out.pickVendor'));
      return;
    }
    const body: PayOutRequest = {
      partnerId,
      amount: sumMoney(allocations.map((a) => a.amount)),
      postingDate: postingDate ?? undefined,
      allocations,
    };
    try {
      const p = await pay.mutateAsync(body);
      notifySuccess(t('payments.out.posted', { payNumber: p?.payNumber ?? '' }));
      close();
      if (p?.id != null) setDetailId(p.id);
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = payments.data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'paymentNumber', label: t('payments.out.paymentNumber'), render: (p) => p.payNumber },
    {
      key: 'vendor',
      label: t('field.vendor'),
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
            {t('payments.out.newPayment')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(p) => p.id ?? p.payNumber ?? ''}
        isLoading={payments.isLoading}
        emptyMessage={t('payments.out.noPayments')}
        onRowClick={(p) => setDetailId(p.id ?? null)}
      />

      <Modal opened={opened} onClose={close} title={t('payments.out.newVendorPayment')} size="lg">
        <Stack>
          <Group grow>
            <PartnerSelect
              label={t('field.vendor')}
              vendor
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
              rows={openBills.map((b) => ({
                id: b.id as number,
                openBalance: b.openBalance,
                label: b.billNumber ?? String(b.id),
              }))}
              allocs={allocs}
              onChange={setAllocs}
              documentLabel={t('payments.out.bill')}
              amountLabel={t('payments.allocate')}
              totalLabel={t('payments.totalLabel', { amount: '' })}
              emptyMessage={t('payments.out.noOpenBills')}
            />
          )}

          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button onClick={submit} loading={pay.isPending}>
              {t('payments.out.postPayment')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      <DetailDrawer
        opened={detailId != null}
        onClose={() => setDetailId(null)}
        title={t('payments.out.drawerTitle', { payNumber: detail.data?.payNumber ?? '' })}
        size="md"
      >
        {detail.data && (
          <>
            <Group>
              <StatusBadge status={detail.data.status} size="md" />
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
              {t('payments.out.postingNote')}
            </Text>
          </>
        )}
      </DetailDrawer>
    </Stack>
  );
}
