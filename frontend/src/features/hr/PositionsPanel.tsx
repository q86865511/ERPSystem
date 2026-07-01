import { Button, Group, Modal, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';
import { DataTable, MoneyText } from '../../components';
import type { DataTableColumn } from '../../components';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreatePosition, usePositions } from './api';

export function PositionsPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const { data, isLoading } = usePositions();
  const create = useCreatePosition();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm({
    initialValues: { code: '', title: '', standardSalary: '' },
    validate: {
      code: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      title: (v) => (v.trim() ? null : t('masterdata.validation.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      await create.mutateAsync({
        code: v.code,
        title: v.title,
        standardSalary: v.standardSalary.trim() || undefined,
      });
      notifySuccess(t('hr.positions.created', { code: v.code }));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'code', label: t('field.code') },
    { key: 'title', label: t('hr.positions.th.title') },
    {
      key: 'standardSalary',
      label: t('hr.positions.th.salary'),
      align: 'right',
      render: (p) => <MoneyText value={p.standardSalary} />,
    },
  ];

  return (
    <Stack>
      {canDo('hr.write') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('hr.positions.new')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(p) => p.id ?? p.code ?? ''}
        isLoading={isLoading}
        emptyMessage={t('hr.positions.empty')}
      />

      <Modal opened={opened} onClose={close} title={t('hr.positions.new')}>
        <form onSubmit={submit}>
          <Stack>
            <TextInput label={t('field.code')} required {...form.getInputProps('code')} />
            <TextInput label={t('hr.positions.form.title')} required {...form.getInputProps('title')} />
            <TextInput
              label={t('hr.positions.form.standardSalary')}
              placeholder="0.00"
              {...form.getInputProps('standardSalary')}
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
