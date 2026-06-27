import { Button, Group, Modal, Stack, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateWarehouse, useWarehouses } from './api';

export function WarehousesPanel() {
  const { canDo } = useAuth();
  const { data, isLoading } = useWarehouses();
  const create = useCreateWarehouse();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm({
    initialValues: { code: '', name: '' },
    validate: {
      code: (v) => (v.trim() ? null : 'Required'),
      name: (v) => (v.trim() ? null : 'Required'),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      await create.mutateAsync({ code: v.code, name: v.name });
      notifySuccess(`Warehouse ${v.code} created`);
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
            New warehouse
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Code</Table.Th>
            <Table.Th>Name</Table.Th>
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
          No warehouses yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New warehouse">
        <form onSubmit={submit}>
          <Stack>
            <TextInput label="Code" required {...form.getInputProps('code')} />
            <TextInput label="Name" required {...form.getInputProps('name')} />
            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={close}>
                Cancel
              </Button>
              <Button type="submit" loading={create.isPending}>
                Create
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </Stack>
  );
}
