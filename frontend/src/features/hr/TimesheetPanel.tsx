import { Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useAuth } from '../../auth/useAuth';
import { DataTable, StateButton } from '../../components';
import type { DataTableColumn } from '../../components';
import { StatusBadge } from '../../components/StatusBadge';
import { useI18n } from '../../i18n';
import type { TranslationKey } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import type { CreateTimesheetRequest, TimesheetStatus } from '../../api/types';
import { useAdvanceTimesheet, useCreateTimesheet, useEmployees, useTimesheets } from './api';

const TIMESHEET_STATUSES: TimesheetStatus[] = ['DRAFT', 'SUBMITTED', 'APPROVED'];

export function TimesheetPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const [filter, setFilter] = useState<string | null>(null);
  const { data, isLoading } = useTimesheets((filter as TimesheetStatus) ?? undefined);
  const employees = useEmployees().data ?? [];
  const create = useCreateTimesheet();
  const submitTs = useAdvanceTimesheet('submit');
  const approveTs = useAdvanceTimesheet('approve');
  const [opened, { open, close }] = useDisclosure(false);

  const empOptions = employees.map((e) => ({
    value: String(e.id),
    label: `${e.code} — ${e.firstName} ${e.lastName}`,
  }));
  const empName = new Map(
    employees.map((e) => [e.id, `${e.firstName ?? ''} ${e.lastName ?? ''}`.trim()]),
  );
  const statusOptions = TIMESHEET_STATUSES.map((s) => ({ value: s, label: t(`status.${s}` as TranslationKey) }));

  const form = useForm<{ employeeId: string; weekEnding: string | null; regularHours: string; overtimeHours: string; note: string }>({
    initialValues: {
      employeeId: '',
      weekEnding: dayjs().format('YYYY-MM-DD'),
      regularHours: '40',
      overtimeHours: '0',
      note: '',
    },
    validate: {
      employeeId: (v) => (v ? null : t('masterdata.validation.required')),
      weekEnding: (v) => (v ? null : t('masterdata.validation.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      const body: CreateTimesheetRequest = {
        employeeId: Number(v.employeeId),
        weekEnding: v.weekEnding ?? undefined,
        regularHours: v.regularHours.trim() || undefined,
        overtimeHours: v.overtimeHours.trim() || undefined,
        note: v.note.trim() || undefined,
      };
      await create.mutateAsync(body);
      notifySuccess(t('hr.timesheet.logged'));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const advance = async (id: number, action: 'submit' | 'approve') => {
    try {
      if (action === 'submit') {
        await submitTs.mutateAsync(id);
        notifySuccess(t('hr.timesheet.submitted'));
      } else {
        await approveTs.mutateAsync(id);
        notifySuccess(t('hr.timesheet.approved'));
      }
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'employee', label: t('hr.common.employee'), render: (r) => empName.get(r.employeeId) ?? '—' },
    { key: 'weekEnding', label: t('hr.timesheet.th.weekEnding'), render: (r) => r.weekEnding },
    { key: 'regular', label: t('hr.timesheet.th.regular'), align: 'right', render: (r) => r.regularHours },
    { key: 'overtime', label: t('hr.timesheet.th.overtime'), align: 'right', render: (r) => r.overtimeHours },
    {
      key: 'total',
      label: t('hr.timesheet.th.total'),
      align: 'right',
      render: (r) => String(Number(r.regularHours ?? 0) + Number(r.overtimeHours ?? 0)),
    },
    { key: 'status', label: t('field.status'), render: (r) => <StatusBadge status={r.status} /> },
    {
      key: 'actions',
      label: '',
      render: (r) =>
        canDo('hr.write') ? (
          <Group gap="xs" wrap="nowrap" justify="flex-end">
            <StateButton
              size="xs"
              label={t('hr.timesheet.submit')}
              onClick={() => advance(r.id ?? 0, 'submit')}
              disabled={r.status !== 'DRAFT'}
              disabledReason={t('hr.timesheet.onlyDraft')}
            />
            <StateButton
              size="xs"
              variant="light"
              label={t('hr.timesheet.approve')}
              onClick={() => advance(r.id ?? 0, 'approve')}
              disabled={r.status !== 'SUBMITTED'}
              disabledReason={t('hr.timesheet.onlySubmitted')}
            />
          </Group>
        ) : null,
    },
  ];

  return (
    <Stack>
      <Group justify="space-between">
        <Select
          placeholder={t('hr.common.allStatuses')}
          data={statusOptions}
          value={filter}
          onChange={setFilter}
          clearable
          w={220}
        />
        {canDo('hr.write') && (
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('hr.timesheet.new')}
          </Button>
        )}
      </Group>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id ?? 0}
        isLoading={isLoading}
        emptyMessage={t('hr.timesheet.empty')}
        minWidth={820}
      />

      <Modal opened={opened} onClose={close} title={t('hr.timesheet.new')}>
        <form onSubmit={submit}>
          <Stack>
            <Select
              label={t('hr.common.employee')}
              required
              searchable
              data={empOptions}
              {...form.getInputProps('employeeId')}
            />
            <DateInput
              label={t('hr.timesheet.form.weekEnding')}
              value={form.values.weekEnding}
              onChange={(d) => form.setFieldValue('weekEnding', d)}
            />
            <Group grow>
              <TextInput label={t('hr.timesheet.form.regular')} placeholder="40" {...form.getInputProps('regularHours')} />
              <TextInput label={t('hr.timesheet.form.overtime')} placeholder="0" {...form.getInputProps('overtimeHours')} />
            </Group>
            <TextInput label={t('hr.common.note')} {...form.getInputProps('note')} />
            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={close}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" loading={create.isPending}>
                {t('common.create')}
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </Stack>
  );
}
