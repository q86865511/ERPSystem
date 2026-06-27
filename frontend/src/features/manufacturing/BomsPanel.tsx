import { useState } from 'react';
import { ActionIcon, Button, Drawer, Group, Modal, Stack, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import type { BomResponse, CreateBomRequest } from '../../api/types';
import { ItemSelect } from '../../components/EntitySelect';
import { StatusBadge } from '../../components/StatusBadge';
import { useItemMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useBoms, useCreateBom } from './api';

type CompForm = { componentItemId: number | null; qtyPer: string; scrapPct: string };
const emptyComp = (): CompForm => ({ componentItemId: null, qtyPer: '', scrapPct: '0' });

export function BomsPanel() {
  const { canDo } = useAuth();
  const { data, isLoading } = useBoms();
  const items = useItemMap();
  const create = useCreateBom();
  const [opened, { open, close }] = useDisclosure(false);
  const [detail, setDetail] = useState<BomResponse | null>(null);

  const form = useForm<{ parentItemId: number | null; outputQty: string; components: CompForm[] }>({
    initialValues: { parentItemId: null, outputQty: '1', components: [emptyComp()] },
    validate: {
      parentItemId: (v) => (v != null ? null : 'Pick a finished item'),
      outputQty: (v) => (v ? null : 'Required'),
    },
  });

  const submit = form.onSubmit(async (v) => {
    const components = v.components.filter((c) => c.componentItemId != null && c.qtyPer);
    if (components.length === 0) {
      notifyError('Add at least one component');
      return;
    }
    const body: CreateBomRequest = {
      parentItemId: v.parentItemId ?? undefined,
      outputQty: v.outputQty,
      components: components.map((c) => ({
        componentItemId: c.componentItemId ?? undefined,
        qtyPer: c.qtyPer,
        scrapPct: c.scrapPct || '0',
      })),
    };
    try {
      const bom = await create.mutateAsync(body);
      notifySuccess(`BOM v${bom?.version} created`);
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];
  return (
    <Stack>
      {canDo('manufacturing.write') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            New BOM
          </Button>
        </Group>
      )}

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Parent item</Table.Th>
            <Table.Th ta="right">Version</Table.Th>
            <Table.Th ta="right">Output qty</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((b) => (
            <Table.Tr key={b.id}>
              <Table.Td>{b.parentItemId != null ? (items.get(b.parentItemId) ?? b.parentItemId) : '—'}</Table.Td>
              <Table.Td ta="right">{b.version}</Table.Td>
              <Table.Td ta="right">{b.outputQty}</Table.Td>
              <Table.Td>
                <StatusBadge status={b.status} />
              </Table.Td>
              <Table.Td ta="right">
                <Button size="xs" variant="subtle" onClick={() => setDetail(b)}>
                  View
                </Button>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          No bills of material yet.
        </Text>
      )}

      <Modal opened={opened} onClose={close} title="New bill of materials" size="xl">
        <form onSubmit={submit}>
          <Stack>
            <Group grow>
              <ItemSelect
                label="Parent (finished) item"
                itemType="FINISHED"
                required
                value={form.values.parentItemId}
                onChange={(id) => form.setFieldValue('parentItemId', id)}
                error={form.errors.parentItemId}
              />
              <TextInput label="Output qty" {...form.getInputProps('outputQty')} />
            </Group>
            <Text fw={600} size="sm">
              Components
            </Text>
            {form.values.components.map((_, i) => (
              <Group key={i} align="flex-end" wrap="nowrap">
                <ItemSelect
                  label={i === 0 ? 'Component' : undefined}
                  value={form.values.components[i]?.componentItemId ?? null}
                  onChange={(id) => form.setFieldValue(`components.${i}.componentItemId`, id)}
                  style={{ flex: 1 }}
                />
                <TextInput label={i === 0 ? 'Qty per' : undefined} w={100} {...form.getInputProps(`components.${i}.qtyPer`)} />
                <TextInput label={i === 0 ? 'Scrap %' : undefined} w={100} {...form.getInputProps(`components.${i}.scrapPct`)} />
                <ActionIcon
                  variant="subtle"
                  color="red"
                  disabled={form.values.components.length === 1}
                  onClick={() => form.removeListItem('components', i)}
                >
                  <IconTrash size={16} />
                </ActionIcon>
              </Group>
            ))}
            <Group>
              <Button
                size="xs"
                variant="default"
                leftSection={<IconPlus size={14} />}
                onClick={() => form.insertListItem('components', emptyComp())}
              >
                Add component
              </Button>
            </Group>
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

      <Drawer
        opened={detail != null}
        onClose={() => setDetail(null)}
        position="right"
        size="lg"
        title={`BOM ${detail?.parentItemId != null ? (items.get(detail.parentItemId) ?? '') : ''} v${detail?.version ?? ''}`}
      >
        {detail && (
          <Stack>
            <Group>
              <StatusBadge status={detail.status} />
              <Text size="sm" c="dimmed">
                Output qty {detail.outputQty}
              </Text>
            </Group>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Component</Table.Th>
                  <Table.Th ta="right">Qty per</Table.Th>
                  <Table.Th ta="right">Scrap %</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(detail.components ?? []).map((c) => (
                  <Table.Tr key={c.id}>
                    <Table.Td>
                      {c.componentItemId != null ? (items.get(c.componentItemId) ?? c.componentItemId) : '—'}
                    </Table.Td>
                    <Table.Td ta="right">{c.qtyPer}</Table.Td>
                    <Table.Td ta="right">{c.scrapPct}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}
