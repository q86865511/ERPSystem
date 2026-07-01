import { Button, Group, Modal, Select, Stack, TextInput, Textarea } from '@mantine/core';
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
import { LEAVE_TYPES } from '../../api/types';
import type { CreateLeaveRequest, LeaveStatus } from '../../api/types';
import { useDecideLeave, useEmployees, useLeaveRequests, useSubmitLeave } from './api';

const LEAVE_STATUSES: LeaveStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];

export function LeavePanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const [filter, setFilter] = useState<string | null>(null);
  const { data, isLoading } = useLeaveRequests((filter as LeaveStatus) ?? undefined);
  const employees = useEmployees().data ?? [];
  const submitLeave = useSubmitLeave();
  const approve = useDecideLeave('approve');
  const reject = useDecideLeave('reject');
  const [opened, { open, close }] = useDisclosure(false);

  const empOptions = employees.map((e) => ({
    value: String(e.id),
    label: `${e.code} — ${e.firstName} ${e.lastName}`,
  }));
  const empName = new Map(
    employees.map((e) => [e.id, `${e.firstName ?? ''} ${e.lastName ?? ''}`.trim()]),
  );
  const typeOptions = LEAVE_TYPES.map((s) => ({ value: s, label: t(`hr.leave.type.${s}` as TranslationKey) }));
  const statusOptions = LEAVE_STATUSES.map((s) => ({ value: s, label: t(`status.${s}` as TranslationKey) }));

  const form = useForm<{ employeeId: string; leaveType: string; startDate: string | null; endDate: string | null; days: string; reason: string }>({
    initialValues: {
      employeeId: '',
      leaveType: 'ANNUAL',
      startDate: dayjs().format('YYYY-MM-DD'),
      endDate: dayjs().format('YYYY-MM-DD'),
      days: '1',
      reason: '',
    },
    validate: {
      employeeId: (v) => (v ? null : t('masterdata.validation.required')),
      days: (v) => (Number(v) > 0 ? null : t('masterdata.validation.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      const body: CreateLeaveRequest = {
        employeeId: Number(v.employeeId),
        leaveType: v.leaveType as CreateLeaveRequest['leaveType'],
        startDate: v.startDate ?? undefined,
        endDate: v.endDate ?? undefined,
        days: v.days.trim() || undefined,
        reason: v.reason.trim() || undefined,
      };
      await submitLeave.mutateAsync(body);
      notifySuccess(t('hr.leave.filed'));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const decide = async (id: number, which: 'approve' | 'reject') => {
    try {
      if (which === 'approve') {
        await approve.mutateAsync(id);
        notifySuccess(t('hr.leave.approved'));
      } else {
        await reject.mutateAsync(id);
        notifySuccess(t('hr.leave.rejected'));
      }
    } catch (e) {
      notifyError(e);
    }
  };

  const rows = data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'employee', label: t('hr.common.employee'), render: (r) => empName.get(r.employeeId) ?? '—' },
    { key: 'type', label: t('hr.leave.th.type'), render: (r) => t(`hr.leave.type.${r.leaveType}` as TranslationKey) },
    { key: 'from', label: t('hr.leave.th.from'), render: (r) => r.startDate },
    { key: 'to', label: t('hr.leave.th.to'), render: (r) => r.endDate },
    { key: 'days', label: t('hr.leave.th.days'), align: 'right', render: (r) => r.days },
    { key: 'status', label: t('field.status'), render: (r) => <StatusBadge status={r.status} /> },
    { key: 'decidedBy', label: t('hr.leave.th.decidedBy'), render: (r) => r.decidedBy ?? '—' },
    {
      key: 'actions',
      label: '',
      render: (r) =>
        canDo('hr.write') ? (
          <Group gap="xs" wrap="nowrap" justify="flex-end">
            <StateButton
              size="xs"
              label={t('hr.leave.approve')}
              onClick={() => decide(r.id ?? 0, 'approve')}
              disabled={r.status !== 'PENDING'}
              disabledReason={t('hr.leave.onlyPending')}
            />
            <StateButton
              size="xs"
              variant="light"
              color="red"
              label={t('hr.leave.reject')}
              onClick={() => decide(r.id ?? 0, 'reject')}
              disabled={r.status !== 'PENDING'}
              disabledReason={t('hr.leave.onlyPending')}
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
            {t('hr.leave.new')}
          </Button>
        )}
      </Group>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id ?? 0}
        isLoading={isLoading}
        emptyMessage={t('hr.leave.empty')}
        minWidth={860}
      />

      <Modal opened={opened} onClose={close} title={t('hr.leave.new')}>
        <form onSubmit={submit}>
          <Stack>
            <Select
              label={t('hr.common.employee')}
              required
              searchable
              data={empOptions}
              {...form.getInputProps('employeeId')}
            />
            <Group grow>
              <Select
                label={t('hr.leave.form.type')}
                data={typeOptions}
                allowDeselect={false}
                {...form.getInputProps('leaveType')}
              />
              <TextInput label={t('hr.leave.form.days')} placeholder="1" {...form.getInputProps('days')} />
            </Group>
            <Group grow>
              <DateInput
                label={t('hr.leave.form.startDate')}
                value={form.values.startDate}
                onChange={(d) => form.setFieldValue('startDate', d)}
              />
              <DateInput
                label={t('hr.leave.form.endDate')}
                value={form.values.endDate}
                onChange={(d) => form.setFieldValue('endDate', d)}
              />
            </Group>
            <Textarea label={t('hr.leave.form.reason')} autosize minRows={2} {...form.getInputProps('reason')} />
            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={close}>
                {t('common.cancel')}
              </Button>
              <Button type="submit" loading={submitLeave.isPending}>
                {t('common.create')}
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </Stack>
  );
}
