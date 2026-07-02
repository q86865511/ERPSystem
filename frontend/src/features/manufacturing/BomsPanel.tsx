import { useState } from 'react';
import { ActionIcon, Button, Group, Modal, Stack, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import type { BomResponse, CreateBomRequest } from '../../api/types';
import { ItemSelect } from '../../components/EntitySelect';
import { DataTable, DetailDrawer, StatusBadge } from '../../components';
import type { DataTableColumn } from '../../components';
import { useItemMap } from '../masterdata/api';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useBoms, useCreateBom } from './api';

type CompForm = { componentItemId: number | null; qtyPer: string; scrapPct: string };
const emptyComp = (): CompForm => ({ componentItemId: null, qtyPer: '', scrapPct: '0' });

export function BomsPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const { data, isLoading } = useBoms();
  const items = useItemMap();
  const create = useCreateBom();
  const [opened, { open, close }] = useDisclosure(false);
  const [detail, setDetail] = useState<BomResponse | null>(null);

  const form = useForm<{ parentItemId: number | null; outputQty: string; components: CompForm[] }>({
    initialValues: { parentItemId: null, outputQty: '1', components: [emptyComp()] },
    validate: {
      parentItemId: (v) => (v != null ? null : t('manufacturing.bom.pickFinishedItem')),
      outputQty: (v) => (v ? null : t('manufacturing.bom.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    const components = v.components
      .filter(
        (c): c is { componentItemId: number; qtyPer: string; scrapPct: string } =>
          c.componentItemId != null && c.qtyPer !== '',
      )
      .map((c) => ({ componentItemId: c.componentItemId, qtyPer: c.qtyPer, scrapPct: c.scrapPct || '0' }));
    if (components.length === 0) {
      notifyError(t('manufacturing.bom.addAtLeastOneComponent'));
      return;
    }
    if (v.parentItemId == null) {
      notifyError(t('manufacturing.bom.pickFinishedItem'));
      return;
    }
    const body: CreateBomRequest = {
      parentItemId: v.parentItemId,
      outputQty: v.outputQty,
      components,
    };
    try {
      const bom = await create.mutateAsync(body);
      notifySuccess(t('manufacturing.bom.created', { version: bom?.version ?? '' }));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];
  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    {
      key: 'parentItem',
      label: t('manufacturing.bom.parentItem'),
      render: (b) => (b.parentItemId != null ? (items.get(b.parentItemId) ?? b.parentItemId) : '—'),
    },
    { key: 'version', label: t('manufacturing.bom.version'), align: 'right' },
    { key: 'outputQty', label: t('manufacturing.bom.outputQty'), align: 'right' },
    { key: 'status', label: t('field.status'), render: (b) => <StatusBadge status={b.status} /> },
  ];

  return (
    <Stack>
      {canDo('manufacturing.write') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('manufacturing.bom.newBom')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(b) => b.id ?? ''}
        isLoading={isLoading}
        emptyMessage={t('manufacturing.bom.empty')}
        onRowClick={(b) => setDetail(b)}
      />

      <Modal opened={opened} onClose={close} title={t('manufacturing.bom.newBomTitle')} size="xl">
        <form onSubmit={submit}>
          <Stack>
            <Group grow>
              <ItemSelect
                label={t('manufacturing.bom.parentItemLabel')}
                itemType="FINISHED"
                required
                value={form.values.parentItemId}
                onChange={(id) => form.setFieldValue('parentItemId', id)}
                error={form.errors.parentItemId}
              />
              <TextInput label={t('manufacturing.bom.outputQty')} {...form.getInputProps('outputQty')} />
            </Group>
            <Text fw={600} size="sm">
              {t('manufacturing.bom.components')}
            </Text>
            {form.values.components.map((_, i) => (
              <Group key={i} align="flex-end" wrap="nowrap">
                <ItemSelect
                  label={i === 0 ? t('manufacturing.bom.component') : undefined}
                  value={form.values.components[i]?.componentItemId ?? null}
                  onChange={(id) => form.setFieldValue(`components.${i}.componentItemId`, id)}
                  style={{ flex: 1 }}
                />
                <TextInput label={i === 0 ? t('manufacturing.bom.qtyPer') : undefined} w={100} {...form.getInputProps(`components.${i}.qtyPer`)} />
                <TextInput label={i === 0 ? t('manufacturing.bom.scrapPct') : undefined} w={100} {...form.getInputProps(`components.${i}.scrapPct`)} />
                <ActionIcon
                  variant="subtle"
                  color="red"
                  aria-label={t('common.removeLine')}
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
                {t('manufacturing.bom.addComponent')}
              </Button>
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

      <DetailDrawer
        opened={detail != null}
        onClose={() => setDetail(null)}
        title={t('manufacturing.bom.drawerTitle', {
          item: detail?.parentItemId != null ? (items.get(detail.parentItemId) ?? '') : '',
          version: detail?.version ?? '',
        })}
        size="lg"
      >
        {detail && (
          <>
            <Group>
              <StatusBadge status={detail.status} />
              <Text size="sm" c="dimmed">
                {t('manufacturing.bom.outputQtyValue', { qty: detail.outputQty ?? '' })}
              </Text>
            </Group>
            <Table>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>{t('manufacturing.bom.component')}</Table.Th>
                  <Table.Th ta="right">{t('manufacturing.bom.qtyPer')}</Table.Th>
                  <Table.Th ta="right">{t('manufacturing.bom.scrapPct')}</Table.Th>
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
          </>
        )}
      </DetailDrawer>
    </Stack>
  );
}
