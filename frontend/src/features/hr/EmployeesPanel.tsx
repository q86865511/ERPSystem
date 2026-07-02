import { Badge, Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import dayjs from 'dayjs';
import { useAuth } from '../../auth/useAuth';
import { DataTable, MoneyText } from '../../components';
import type { DataTableColumn } from '../../components';
import { useI18n } from '../../i18n';
import type { TranslationKey } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import type { CreateEmployeeRequest } from '../../api/types';
import { useCreateEmployee, useDepartments, useEmployees, usePositions } from './api';

const STATUSES = ['ACTIVE', 'ON_LEAVE', 'TERMINATED'] as const;

export function EmployeesPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const { data, isLoading } = useEmployees();
  const departments = useDepartments().data ?? [];
  const positions = usePositions().data ?? [];
  const create = useCreateEmployee();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm<{
    code: string;
    firstName: string;
    lastName: string;
    departmentId: string;
    positionId: string;
    monthlySalary: string;
    hireDate: string | null;
    status: string;
  }>({
    initialValues: {
      code: '',
      firstName: '',
      lastName: '',
      departmentId: '',
      positionId: '',
      monthlySalary: '',
      hireDate: dayjs().format('YYYY-MM-DD'),
      status: 'ACTIVE',
    },
    validate: {
      code: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      firstName: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      lastName: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      departmentId: (v) => (v ? null : t('masterdata.validation.required')),
      positionId: (v) => (v ? null : t('masterdata.validation.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    if (v.hireDate == null) {
      notifyError(t('masterdata.validation.required'));
      return;
    }
    try {
      const body: CreateEmployeeRequest = {
        code: v.code,
        firstName: v.firstName,
        lastName: v.lastName,
        departmentId: Number(v.departmentId),
        positionId: Number(v.positionId),
        monthlySalary: v.monthlySalary.trim() || undefined,
        status: v.status as CreateEmployeeRequest['status'],
        hireDate: v.hireDate,
      };
      await create.mutateAsync(body);
      notifySuccess(t('hr.employees.created', { code: v.code }));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const deptOptions = departments.map((d) => ({ value: String(d.id), label: `${d.code} — ${d.name}` }));
  const posOptions = positions.map((p) => ({ value: String(p.id), label: `${p.code} — ${p.title}` }));
  const deptMap = new Map(departments.map((d) => [d.id, d.name]));
  const posMap = new Map(positions.map((p) => [p.id, p.title]));
  const statusOptions = STATUSES.map((s) => ({
    value: s,
    label: t(`hr.employmentStatus.${s}` as TranslationKey),
  }));

  const rows = data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'code', label: t('field.code') },
    {
      key: 'name',
      label: t('hr.employees.th.name'),
      render: (e) => `${e.firstName ?? ''} ${e.lastName ?? ''}`.trim(),
    },
    {
      key: 'department',
      label: t('hr.employees.th.department'),
      render: (e) => deptMap.get(e.departmentId) ?? '—',
    },
    {
      key: 'position',
      label: t('hr.employees.th.position'),
      render: (e) => posMap.get(e.positionId) ?? '—',
    },
    {
      key: 'salary',
      label: t('hr.employees.th.salary'),
      align: 'right',
      render: (e) => <MoneyText value={e.monthlySalary} />,
    },
    {
      key: 'status',
      label: t('field.status'),
      render: (e) => (
        <Badge
          variant="light"
          color={e.status === 'ACTIVE' ? 'teal' : e.status === 'ON_LEAVE' ? 'orange' : 'gray'}
        >
          {t(`hr.employmentStatus.${e.status ?? 'ACTIVE'}` as TranslationKey)}
        </Badge>
      ),
    },
  ];

  return (
    <Stack>
      {canDo('hr.write') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('hr.employees.new')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(e) => e.id ?? e.code ?? ''}
        isLoading={isLoading}
        emptyMessage={t('hr.employees.empty')}
        minWidth={760}
      />

      <Modal opened={opened} onClose={close} title={t('hr.employees.new')}>
        <form onSubmit={submit}>
          <Stack>
            <TextInput label={t('field.code')} required {...form.getInputProps('code')} />
            <Group grow>
              <TextInput label={t('hr.employees.form.firstName')} required {...form.getInputProps('firstName')} />
              <TextInput label={t('hr.employees.form.lastName')} required {...form.getInputProps('lastName')} />
            </Group>
            <Select
              label={t('hr.employees.form.department')}
              required
              searchable
              data={deptOptions}
              {...form.getInputProps('departmentId')}
            />
            <Select
              label={t('hr.employees.form.position')}
              required
              searchable
              data={posOptions}
              {...form.getInputProps('positionId')}
            />
            <TextInput label={t('hr.employees.form.salary')} placeholder="0.00" {...form.getInputProps('monthlySalary')} />
            <Group grow>
              <DateInput
                label={t('hr.employees.form.hireDate')}
                value={form.values.hireDate}
                onChange={(d) => form.setFieldValue('hireDate', d)}
              />
              <Select
                label={t('hr.employees.form.status')}
                data={statusOptions}
                allowDeselect={false}
                {...form.getInputProps('status')}
              />
            </Group>
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
