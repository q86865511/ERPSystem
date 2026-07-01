import { Button, Group, Modal, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';
import { DataTable } from '../../components';
import type { DataTableColumn } from '../../components';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateDepartment, useDepartments } from './api';

export function DepartmentsPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const { data, isLoading } = useDepartments();
  const create = useCreateDepartment();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm({
    initialValues: { code: '', name: '', budgetAccountCode: '' },
    validate: {
      code: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      name: (v) => (v.trim() ? null : t('masterdata.validation.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      await create.mutateAsync({
        code: v.code,
        name: v.name,
        budgetAccountCode: v.budgetAccountCode.trim() || undefined,
      });
      notifySuccess(t('hr.departments.created', { code: v.code }));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'code', label: t('field.code') },
    { key: 'name', label: t('field.name') },
    {
      key: 'budgetAccountCode',
      label: t('hr.departments.th.budget'),
      render: (d) => d.budgetAccountCode ?? '—',
    },
  ];

  return (
    <Stack>
      {canDo('hr.write') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('hr.departments.new')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(d) => d.id ?? d.code ?? ''}
        isLoading={isLoading}
        emptyMessage={t('hr.departments.empty')}
      />

      <Modal opened={opened} onClose={close} title={t('hr.departments.new')}>
        <form onSubmit={submit}>
          <Stack>
            <TextInput label={t('field.code')} required {...form.getInputProps('code')} />
            <TextInput label={t('field.name')} required {...form.getInputProps('name')} />
            <TextInput
              label={t('hr.departments.form.budgetAccount')}
              placeholder="6100"
              {...form.getInputProps('budgetAccountCode')}
            />
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
