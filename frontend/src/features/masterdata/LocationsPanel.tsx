import { useMemo, useState } from 'react';
import { Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { LOCATION_TYPES, type CreateLocationRequest } from '../../api/types';
import { DataTable } from '../../components';
import type { DataTableColumn } from '../../components';
import { WarehouseSelect } from '../../components/EntitySelect';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import type { TranslationKey } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateLocation, useLocations, useWarehouses } from './api';

export function LocationsPanel() {
  const { t } = useI18n();
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
      warehouseId: (v) => (v != null ? null : t('masterdata.validation.required')),
      code: (v) => (v.trim() ? null : t('masterdata.validation.required')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    if (v.warehouseId == null) {
      notifyError(t('masterdata.validation.required'));
      return;
    }
    try {
      await create.mutateAsync({
        warehouseId: v.warehouseId,
        code: v.code,
        locationType: v.locationType as CreateLocationRequest['locationType'],
      });
      notifySuccess(t('masterdata.locations.created', { code: v.code }));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const locationTypeOptions = LOCATION_TYPES.map((v) => ({
    value: v,
    label: t(`locationType.${v}` as TranslationKey),
  }));
  const rows = data ?? [];

  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'code', label: t('field.code') },
    {
      key: 'locationType',
      label: t('field.type'),
      render: (l) => t(`locationType.${l.locationType}` as TranslationKey),
    },
    {
      key: 'warehouse',
      label: t('field.warehouse'),
      render: (l) => (l.warehouseId != null ? (warehouseCode.get(l.warehouseId) ?? l.warehouseId) : '—'),
    },
  ];

  return (
    <Stack>
      <Group justify="space-between">
        <WarehouseSelect
          label={t('masterdata.locations.filterByWarehouse')}
          placeholder={t('masterdata.locations.allWarehouses')}
          clearable
          value={filterWarehouseId}
          onChange={setFilterWarehouseId}
          w={260}
        />
        {canDo('masterdata.create') && (
          <Button leftSection={<IconPlus size={16} />} onClick={open} mt="auto">
            {t('masterdata.locations.new')}
          </Button>
        )}
      </Group>

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(l) => l.id ?? l.code ?? ''}
        isLoading={isLoading}
        emptyMessage={t('masterdata.locations.empty')}
      />

      <Modal opened={opened} onClose={close} title={t('masterdata.locations.new')}>
        <form onSubmit={submit}>
          <Stack>
            <WarehouseSelect
              label={t('field.warehouse')}
              required
              value={form.values.warehouseId}
              onChange={(id) => form.setFieldValue('warehouseId', id)}
              error={form.errors.warehouseId}
            />
            <TextInput label={t('field.code')} required {...form.getInputProps('code')} />
            <Select
              label={t('field.type')}
              data={locationTypeOptions}
              allowDeselect={false}
              {...form.getInputProps('locationType')}
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
