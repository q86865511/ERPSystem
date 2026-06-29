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
  const total = sumMoney(Object.values(allocs));

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
  return (
    <Stack>
      {canDo('payments.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            {t('payments.in.newReceipt')}
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('payments.in.receiptNumber')}</Table.Th>
            <Table.Th>{t('field.customer')}</Table.Th>
            <Table.Th ta="right">{t('field.amount')}</Table.Th>
            <Table.Th>{t('field.status')}</Table.Th>
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
                  {t('common.view')}
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!payments.isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          {t('payments.in.noReceipts')}
        </Text>
      )}

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
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('payments.in.invoice')}</Table.Th>
                  <Table.Th ta="right">{t('payments.open')}</Table.Th>
                  <Table.Th ta="right" w={140}>
                    {t('payments.allocate')}
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
                        {t('payments.in.noOpenInvoices')}
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                )}
              </Table.Tbody>
            </Table>
          )}

          <Group justify="space-between">
            <Text fw={600}>{t('payments.totalLabel', { amount: formatMoney(total) })}</Text>
            <Group>
              <Button variant="default" onClick={close}>
                {t('common.cancel')}
              </Button>
              <Button onClick={submit} loading={pay.isPending}>
                {t('payments.in.postReceipt')}
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
        title={t('payments.in.drawerTitle', { payNumber: detail.data?.payNumber ?? '' })}
      >
        {detail.data && (
          <Stack>
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
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
