import { Button, Group, Modal, Select, Stack, Switch, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { ITEM_TYPES, type CreateItemRequest } from '../../api/types';
import { DataTable, MoneyText } from '../../components';
import type { DataTableColumn } from '../../components';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import type { TranslationKey } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateItem, useItems } from './api';

export function ItemsPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const { data, isLoading } = useItems();
  const create = useCreateItem();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm({
    initialValues: { sku: '', name: '', itemType: 'RAW', uom: 'EA', stocked: true, standardCost: '' },
    validate: {
      sku: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      name: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      uom: (v) => (v.trim() ? null : t('masterdata.validation.required')),
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
      notifySuccess(t('masterdata.items.created', { sku: v.sku }));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const itemTypeOptions = ITEM_TYPES.map((v) => ({ value: v, label: t(`itemType.${v}` as TranslationKey) }));
  const rows = data ?? [];

  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'sku', label: t('masterdata.items.th.sku') },
    { key: 'name', label: t('field.name') },
    {
      key: 'itemType',
      label: t('field.type'),
      render: (i) => t(`itemType.${i.itemType}` as TranslationKey),
    },
    { key: 'uom', label: t('masterdata.items.th.uom') },
    {
      key: 'stocked',
      label: t('masterdata.items.th.stocked'),
      render: (i) => (i.stocked ? t('masterdata.items.yes') : t('masterdata.items.no')),
    },
    {
      key: 'standardCost',
      label: t('masterdata.items.th.stdCost'),
      align: 'right',
      render: (i) => <MoneyText value={i.standardCost} />,
    },
  ];

  return (
    <Stack>
      {canDo('masterdata.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('masterdata.items.new')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(i) => i.id ?? i.sku ?? ''}
        isLoading={isLoading}
        emptyMessage={t('masterdata.items.empty')}
        minWidth={640}
      />

      <Modal opened={opened} onClose={close} title={t('masterdata.items.new')}>
        <form onSubmit={submit}>
          <Stack>
            <TextInput label={t('masterdata.items.form.sku')} required {...form.getInputProps('sku')} />
            <TextInput label={t('field.name')} required {...form.getInputProps('name')} />
            <Select
              label={t('field.type')}
              data={itemTypeOptions}
              allowDeselect={false}
              {...form.getInputProps('itemType')}
            />
            <TextInput label={t('masterdata.items.form.uom')} required {...form.getInputProps('uom')} />
            <TextInput
              label={t('masterdata.items.form.standardCost')}
              placeholder="0.00"
              {...form.getInputProps('standardCost')}
            />
            <Switch
              label={t('masterdata.items.form.stocked')}
              {...form.getInputProps('stocked', { type: 'checkbox' })}
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
