import { useMemo, useState } from 'react';
import { Button, Group, Modal, Select, Stack, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { LOCATION_TYPES, type CreateLocationRequest } from '../../api/types';
import { WarehouseSelect } from '../../components/EntitySelect';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateLocation, useLocations, useWarehouses } from './api';

export function LocationsPanel() {
  const { canDo } = useAuth();
  const [filterWarehouseId, setFilterWarehouseId] = useState<number | null>(null);
  const { data, isLoading } = useLocations(filterWarehouseId ?? undefined);
  const { data: warehouses } = useWarehouses();
  const create = useCreateLocation();
  const [opened, { open, close }] = useDisclosure(false);

  const warehouseCode = useMemo(() => {
    const map = new Map<number, string>();
    for (const w of warehouses ?? []) if (w.id != null) map.set(w.id, w.code ?? String(w.id));
    return map;
  }, [warehouses]);

  const form = useForm({
    initialValues: { warehouseId: null as number | null, code: '', locationType: 'STOCK' },
    validate: {
      warehouseId: (v) => (v != null ? null : 'Required'),
      code: (v) => (v.trim() ? null : 'Required'),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      await create.mutateAsync({
        warehouseId: v.warehouseId ?? undefined,
        code: v.code,
        locationType: v.locationType as CreateLocationRequest['locationType'],
      });
      notifySuccess(`Location ${v.code} created`);
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];
  return (
    <Stack>
      <Group justify="space-between">
        <WarehouseSelect
          label="Filter by warehouse"
          placeholder="All warehouses"
          clearable
          value={filterWarehouseId}
          onChange={setFilterWarehouseId}
          w={260}
        />
        {canDo('masterdata.create') && (
          <Button leftSection={<IconPlus size={16} />} onClick={open} mt="auto">
            New location
          </Button>
        )}
      </Group>

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Code</Table.Th>
            <Table.Th>Type</Table.Th>
            <Table.Th>Warehouse</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((l) => (
            <Table.Tr key={l.id}>
              <Table.Td>{l.code}</Table.Td>
              <Table.Td>{l.locationType}</Table.Td>
              <Table.Td>{l.warehouseId != null ? (warehouseCode.get(l.warehouseId) ?? l.warehouseId) : '—'}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No locations.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New location">
        <form onSubmit={submit}>
          <Stack>
            <WarehouseSelect
              label="Warehouse"
              required
              value={form.values.warehouseId}
              onChange={(id) => form.setFieldValue('warehouseId', id)}
              error={form.errors.warehouseId}
            />
            <TextInput label="Code" required {...form.getInputProps('code')} />
            <Select
              label="Type"
              data={LOCATION_TYPES}
              allowDeselect={false}
              {...form.getInputProps('locationType')}
            />
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
