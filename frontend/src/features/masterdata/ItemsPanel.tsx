import { Button, Group, Modal, Select, Stack, Switch, Table, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { ITEM_TYPES, type CreateItemRequest } from '../../api/types';
import { MoneyText } from '../../components/Money';
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
  return (
    <Stack>
      {canDo('masterdata.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('masterdata.items.new')}
          </Button>
        </Group>
      )}

      <Table.ScrollContainer minWidth={640}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('masterdata.items.th.sku')}</Table.Th>
              <Table.Th>{t('field.name')}</Table.Th>
              <Table.Th>{t('field.type')}</Table.Th>
              <Table.Th>{t('masterdata.items.th.uom')}</Table.Th>
              <Table.Th>{t('masterdata.items.th.stocked')}</Table.Th>
              <Table.Th ta="right">{t('masterdata.items.th.stdCost')}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((i) => (
              <Table.Tr key={i.id}>
                <Table.Td>{i.sku}</Table.Td>
                <Table.Td>{i.name}</Table.Td>
                <Table.Td>{t(`itemType.${i.itemType}` as TranslationKey)}</Table.Td>
                <Table.Td>{i.uom}</Table.Td>
                <Table.Td>{i.stocked ? t('masterdata.items.yes') : t('masterdata.items.no')}</Table.Td>
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
          {t('masterdata.items.empty')}
        </Text>
      )}

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
