import { useState } from 'react';
import {
  Badge,
  Button,
  Drawer,
  Group,
  Modal,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { DateInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { PayOutRequest } from '../../api/types';
import { PartnerSelect } from '../../components/EntitySelect';
import { MoneyText, formatMoney, sumMoney } from '../../components/Money';
import { StatusBadge } from '../../components/StatusBadge';
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
  const total = sumMoney(Object.values(allocs));

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
  return (
    <Stack>
      {canDo('payments.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={openForm}>
            {t('payments.out.newPayment')}
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('payments.out.paymentNumber')}</Table.Th>
            <Table.Th>{t('field.vendor')}</Table.Th>
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
          {t('payments.out.noPayments')}
        </Text>
      )}

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
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('payments.out.bill')}</Table.Th>
                  <Table.Th ta="right">{t('payments.open')}</Table.Th>
                  <Table.Th ta="right" w={140}>
                    {t('payments.allocate')}
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {openBills.map((b) => (
                  <Table.Tr key={b.id}>
                    <Table.Td>{b.billNumber}</Table.Td>
                    <Table.Td ta="right">
                      <MoneyText value={b.openBalance} />
                    </Table.Td>
                    <Table.Td>
                      <TextInput
                        size="xs"
                        placeholder={b.openBalance}
                        value={b.id != null ? (allocs[b.id] ?? '') : ''}
                        onChange={(e) =>
                          b.id != null &&
                          setAllocs((a) => ({ ...a, [b.id as number]: e.currentTarget.value }))
                        }
                      />
                    </Table.Td>
                  </Table.Tr>
                ))}
                {openBills.length === 0 && (
                  <Table.Tr>
                    <Table.Td colSpan={3}>
                      <Text c="dimmed" size="sm">
                        {t('payments.out.noOpenBills')}
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
                {t('payments.out.postPayment')}
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
        title={t('payments.out.drawerTitle', { payNumber: detail.data?.payNumber ?? '' })}
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
              {t('payments.out.postingNote')}
            </Text>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
