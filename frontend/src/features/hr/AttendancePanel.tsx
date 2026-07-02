import { Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useAuth } from '../../auth/useAuth';
import { DataTable } from '../../components';
import type { DataTableColumn } from '../../components';
import { StatusBadge } from '../../components/StatusBadge';
import { useI18n } from '../../i18n';
import type { TranslationKey } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { ATTENDANCE_STATUSES } from '../../api/types';
import type { CreateAttendanceRequest } from '../../api/types';
import { useAttendance, useEmployees, useRecordAttendance } from './api';

export function AttendancePanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const [filterEmployee, setFilterEmployee] = useState<string | null>(null);
  const { data, isLoading } = useAttendance(filterEmployee ? Number(filterEmployee) : undefined);
  const employees = useEmployees().data ?? [];
  const create = useRecordAttendance();
  const [opened, { open, close }] = useDisclosure(false);

  const empOptions = employees.map((e) => ({
    value: String(e.id),
    label: `${e.code} — ${e.firstName} ${e.lastName}`,
  }));
  const empName = new Map(
    employees.map((e) => [e.id, `${e.firstName ?? ''} ${e.lastName ?? ''}`.trim()]),
  );
  const statusOptions = ATTENDANCE_STATUSES.map((s) => ({
    value: s,
    label: t(`status.${s}` as TranslationKey),
  }));

  const form = useForm<{ employeeId: string; workDate: string | null; status: string; workedHours: string; note: string }>({
    initialValues: {
      employeeId: '',
      workDate: dayjs().format('YYYY-MM-DD'),
      status: 'PRESENT',
      workedHours: '8',
      note: '',
    },
    validate: {
      employeeId: (v) => (v ? null : t('masterdata.validation.required')),
      workDate: (v) => (v ? null : t('masterdata.validation.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    if (v.workDate == null) {
      notifyError(t('masterdata.validation.required'));
      return;
    }
    try {
      const body: CreateAttendanceRequest = {
        employeeId: Number(v.employeeId),
        workDate: v.workDate,
        status: v.status as CreateAttendanceRequest['status'],
        workedHours: v.workedHours.trim() || undefined,
        note: v.note.trim() || undefined,
      };
      await create.mutateAsync(body);
      notifySuccess(t('hr.attendance.recorded'));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'employee', label: t('hr.common.employee'), render: (a) => empName.get(a.employeeId) ?? '—' },
    { key: 'workDate', label: t('hr.attendance.th.date'), render: (a) => a.workDate },
    { key: 'status', label: t('field.status'), render: (a) => <StatusBadge status={a.status} /> },
    { key: 'hours', label: t('hr.attendance.th.hours'), align: 'right', render: (a) => a.workedHours ?? '—' },
    { key: 'note', label: t('hr.common.note'), render: (a) => a.note ?? '—' },
  ];

  return (
    <Stack>
      <Group justify="space-between">
        <Select
          placeholder={t('hr.common.allEmployees')}
          data={empOptions}
          value={filterEmployee}
          onChange={setFilterEmployee}
          searchable
          clearable
          w={280}
        />
        {canDo('hr.write') && (
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('hr.attendance.new')}
          </Button>
        )}
      </Group>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(a) => a.id ?? 0}
        isLoading={isLoading}
        emptyMessage={t('hr.attendance.empty')}
        minWidth={720}
      />

      <Modal opened={opened} onClose={close} title={t('hr.attendance.new')}>
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
              <DateInput
                label={t('hr.attendance.form.date')}
                value={form.values.workDate}
                onChange={(d) => form.setFieldValue('workDate', d)}
              />
              <Select
                label={t('hr.attendance.form.status')}
                data={statusOptions}
                allowDeselect={false}
                {...form.getInputProps('status')}
              />
            </Group>
            <TextInput label={t('hr.attendance.form.hours')} placeholder="8" {...form.getInputProps('workedHours')} />
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
