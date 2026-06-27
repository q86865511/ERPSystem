import { Button, Group, Modal, Select, Stack, Switch, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { ITEM_TYPES, type CreateItemRequest } from '../../api/types';
import { MoneyText } from '../../components/Money';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateItem, useItems } from './api';

export function ItemsPanel() {
  const { canDo } = useAuth();
  const { data, isLoading } = useItems();
  const create = useCreateItem();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm({
    initialValues: { sku: '', name: '', itemType: 'RAW', uom: 'EA', stocked: true, standardCost: '' },
    validate: {
      sku: (v) => (v.trim() ? null : 'Required'),
      name: (v) => (v.trim() ? null : 'Required'),
      uom: (v) => (v.trim() ? null : 'Required'),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      await create.mutateAsync({
        sku: v.sku,
        name: v.name,
        itemType: v.itemType as CreateItemRequest['itemType'],
        uom: v.uom,
        stocked: v.stocked,
        standardCost: v.standardCost.trim() || undefined,
      });
      notifySuccess(`Item ${v.sku} created`);
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
            New item
          </Button>
        </Group>
      )}

      <Table.ScrollContainer minWidth={640}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>SKU</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>Type</Table.Th>
              <Table.Th>UoM</Table.Th>
              <Table.Th>Stocked</Table.Th>
              <Table.Th ta="right">Std cost</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((i) => (
              <Table.Tr key={i.id}>
                <Table.Td>{i.sku}</Table.Td>
                <Table.Td>{i.name}</Table.Td>
                <Table.Td>{i.itemType}</Table.Td>
                <Table.Td>{i.uom}</Table.Td>
                <Table.Td>{i.stocked ? 'Yes' : 'No'}</Table.Td>
                <Table.Td ta="right">
                  <MoneyText value={i.standardCost} />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {!isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No items yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New item">
        <form onSubmit={submit}>
          <Stack>
            <TextInput label="SKU" required {...form.getInputProps('sku')} />
            <TextInput label="Name" required {...form.getInputProps('name')} />
            <Select label="Type" data={ITEM_TYPES} allowDeselect={false} {...form.getInputProps('itemType')} />
            <TextInput label="Unit of measure" required {...form.getInputProps('uom')} />
            <TextInput label="Standard cost" placeholder="0.00" {...form.getInputProps('standardCost')} />
            <Switch label="Stocked" {...form.getInputProps('stocked', { type: 'checkbox' })} />
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
