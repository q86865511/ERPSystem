import { Button, Group, Modal, Stack, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateWarehouse, useWarehouses } from './api';

export function WarehousesPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const { data, isLoading } = useWarehouses();
  const create = useCreateWarehouse();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm({
    initialValues: { code: '', name: '' },
    validate: {
      code: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      name: (v) => (v.trim() ? null : t('masterdata.validation.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      await create.mutateAsync({ code: v.code, name: v.name });
      notifySuccess(t('masterdata.warehouses.created', { code: v.code }));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];
  return (
    <Stack>
      {canDo('masterdata.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('masterdata.warehouses.new')}
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('field.code')}</Table.Th>
            <Table.Th>{t('field.name')}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((w) => (
            <Table.Tr key={w.id}>
              <Table.Td>{w.code}</Table.Td>
              <Table.Td>{w.name}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          {t('masterdata.warehouses.empty')}
        </Text>
      )}

      <Modal opened={opened} onClose={close} title={t('masterdata.warehouses.new')}>
        <form onSubmit={submit}>
          <Stack>
            <TextInput label={t('field.code')} required {...form.getInputProps('code')} />
            <TextInput label={t('field.name')} required {...form.getInputProps('name')} />
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
