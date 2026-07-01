import { Button, Group, Modal, NumberInput, Select, Stack, Text } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useAuth } from '../../auth/useAuth';
import { DataTable, DetailDrawer, MoneyText, StateButton } from '../../components';
import type { DataTableColumn } from '../../components';
import { StatusBadge } from '../../components/StatusBadge';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import type { PayrollResponse, RunPayrollRequest } from '../../api/types';
import { useEmployees, usePayrolls, usePostPayroll, useRunPayroll } from './api';

const MONTHS = Array.from({ length: 12 }, (_, i) => ({ value: String(i + 1), label: String(i + 1) }));

export function PayrollPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const { data, isLoading } = usePayrolls();
  const employees = useEmployees().data ?? [];
  const run = useRunPayroll();
  const post = usePostPayroll();
  const [opened, { open, close }] = useDisclosure(false);
  const [selected, setSelected] = useState<PayrollResponse | null>(null);

  const empName = new Map(
    employees.map((e) => [e.id, `${e.firstName ?? ''} ${e.lastName ?? ''}`.trim() || e.code]),
  );

  const form = useForm<{ year: number; month: string }>({
    initialValues: { year: dayjs().year(), month: String(dayjs().month() + 1) },
  });

  const submitRun = form.onSubmit(async (v) => {
    try {
      const body: RunPayrollRequest = { year: v.year, month: Number(v.month) };
      await run.mutateAsync(body);
      notifySuccess(t('hr.payroll.ran'));
      close();
    } catch (e) {
      notifyError(e);
    }
  });

  const doPost = async (id: number) => {
    try {
      await post.mutateAsync(id);
      notifySuccess(t('hr.payroll.posted'));
    } catch (e) {
      notifyError(e);
    }
  };

  const period = (p: PayrollResponse) => `${p.periodYear}-${String(p.periodMonth).padStart(2, '0')}`;

  const rows = data ?? [];
  const columns: DataTableColumn<PayrollResponse>[] = [
    { key: 'period', label: t('hr.payroll.th.period'), render: period },
    { key: 'status', label: t('field.status'), render: (p) => <StatusBadge status={p.status} /> },
    { key: 'gross', label: t('hr.payroll.th.gross'), align: 'right', render: (p) => <MoneyText value={p.grossTotal} /> },
    { key: 'tax', label: t('hr.payroll.th.tax'), align: 'right', render: (p) => <MoneyText value={p.taxTotal} /> },
    { key: 'insurance', label: t('hr.payroll.th.insurance'), align: 'right', render: (p) => <MoneyText value={p.insuranceTotal} /> },
    { key: 'net', label: t('hr.payroll.th.net'), align: 'right', render: (p) => <MoneyText value={p.netTotal} /> },
    { key: 'journal', label: t('hr.payroll.th.journal'), render: (p) => (p.journalEntryId ? `#${p.journalEntryId}` : '—') },
    {
      key: 'actions',
      label: '',
      render: (p) =>
        canDo('hr.write') ? (
          <StateButton
            size="xs"
            label={t('hr.payroll.post')}
            onClick={() => doPost(p.id ?? 0)}
            disabled={p.status !== 'DRAFT'}
            disabledReason={t('hr.payroll.onlyDraft')}
          />
        ) : null,
    },
  ];

  const lineColumns: DataTableColumn<NonNullable<PayrollResponse['lines']>[number]>[] = [
    { key: 'employee', label: t('hr.common.employee'), render: (l) => empName.get(l.employeeId) ?? '—' },
    { key: 'gross', label: t('hr.payroll.th.gross'), align: 'right', render: (l) => <MoneyText value={l.gross} /> },
    { key: 'tax', label: t('hr.payroll.th.tax'), align: 'right', render: (l) => <MoneyText value={l.tax} /> },
    { key: 'insurance', label: t('hr.payroll.th.insurance'), align: 'right', render: (l) => <MoneyText value={l.insurance} /> },
    { key: 'net', label: t('hr.payroll.th.net'), align: 'right', render: (l) => <MoneyText value={l.net} /> },
  ];

  return (
    <Stack>
      <Group justify="space-between">
        <Text c="dimmed" size="sm">
          {t('hr.payroll.hint')}
        </Text>
        {canDo('hr.write') && (
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('hr.payroll.run')}
          </Button>
        )}
      </Group>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(p) => p.id ?? 0}
        isLoading={isLoading}
        emptyMessage={t('hr.payroll.empty')}
        onRowClick={setSelected}
        rowActionLabel={t('hr.payroll.viewLines')}
        minWidth={900}
      />

      <Modal opened={opened} onClose={close} title={t('hr.payroll.run')}>
        <form onSubmit={submitRun}>
          <Stack>
            <Group grow>
              <NumberInput label={t('hr.payroll.form.year')} allowDecimal={false} {...form.getInputProps('year')} />
              <Select label={t('hr.payroll.form.month')} data={MONTHS} allowDeselect={false} {...form.getInputProps('month')} />
            </Group>
            <Text size="xs" c="dimmed">
              {t('hr.payroll.rateNote')}
            </Text>
            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={close}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" loading={run.isPending}>
                {t('hr.payroll.run')}
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>

      <DetailDrawer
        opened={selected !== null}
        onClose={() => setSelected(null)}
        title={selected ? `${t('hr.payroll.payslips')} — ${period(selected)}` : ''}
      >
        {selected && (
          <DataTable
            columns={lineColumns}
            rows={selected.lines ?? []}
            rowKey={(l) => l.id ?? 0}
            emptyMessage={t('hr.payroll.empty')}
            minWidth={520}
          />
        )}
      </DetailDrawer>
    </Stack>
  );
}
