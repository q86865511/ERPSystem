import { useMemo, useState } from 'react';
import { Button, Group, Modal, Select, Stack, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { LOCATION_TYPES, type CreateLocationRequest } from '../../api/types';
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
    try {
      await create.mutateAsync({
        warehouseId: v.warehouseId ?? undefined,
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

      <Table striped highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>{t('field.code')}</Table.Th>
            <Table.Th>{t('field.type')}</Table.Th>
            <Table.Th>{t('field.warehouse')}</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((l) => (
            <Table.Tr key={l.id}>
              <Table.Td>{l.code}</Table.Td>
              <Table.Td>{t(`locationType.${l.locationType}` as TranslationKey)}</Table.Td>
              <Table.Td>{l.warehouseId != null ? (warehouseCode.get(l.warehouseId) ?? l.warehouseId) : '—'}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {!isLoading && rows.length === 0 && (
        <Text c="dimmed" ta="center" py="md">
          {t('masterdata.locations.empty')}
        </Text>
      )}

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
