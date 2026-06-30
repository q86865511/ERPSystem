import {
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Stack,
  Switch,
  Text,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { useAuth } from '../../auth/useAuth';
import { DataTable } from '../../components';
import type { DataTableColumn } from '../../components';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreatePartner, usePartners } from './api';

export function PartnersPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const { data, isLoading } = usePartners();
  const create = useCreatePartner();
  const [opened, { open, close }] = useDisclosure(false);

  const form = useForm({
    initialValues: {
      code: '',
      name: '',
      vendor: true,
      customer: false,
      taxId: '',
      paymentTermsDays: 30,
      apAccountCode: '',
      arAccountCode: '',
    },
    validate: {
      code: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      name: (v) => (v.trim() ? null : t('masterdata.validation.required')),
      vendor: (v, values) => (v || values.customer ? null : t('masterdata.validation.pickRole')),
    },
  });

  const submit = form.onSubmit(async (v) => {
    try {
      await create.mutateAsync({
        code: v.code,
        name: v.name,
        vendor: v.vendor,
        customer: v.customer,
        taxId: v.taxId.trim() || undefined,
        paymentTermsDays: Number(v.paymentTermsDays) || 30,
        apAccountCode: v.apAccountCode.trim() || undefined,
        arAccountCode: v.arAccountCode.trim() || undefined,
      });
      notifySuccess(t('masterdata.partners.created', { code: v.code }));
      close();
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  });

  const rows = data ?? [];

  const columns: DataTableColumn<(typeof rows)[number]>[] = [
    { key: 'code', label: t('field.code') },
    { key: 'name', label: t('field.name') },
    {
      key: 'roles',
      label: t('masterdata.partners.th.roles'),
      render: (p) => (
        <Group gap={4}>
          {p.vendor && <Badge variant="light">{t('field.vendor')}</Badge>}
          {p.customer && (
            <Badge variant="light" color="grape">
              {t('field.customer')}
            </Badge>
          )}
        </Group>
      ),
    },
    {
      key: 'taxId',
      label: t('masterdata.partners.th.taxId'),
      render: (p) => p.taxId ?? '—',
    },
    { key: 'paymentTermsDays', label: t('masterdata.partners.th.terms'), align: 'right' },
  ];

  return (
    <Stack>
      {canDo('masterdata.create') && (
        <Group justify="flex-end">
          <Button leftSection={<IconPlus size={16} />} onClick={open}>
            {t('masterdata.partners.new')}
          </Button>
        </Group>
      )}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(p) => p.id ?? p.code ?? ''}
        isLoading={isLoading}
        emptyMessage={t('masterdata.partners.empty')}
        minWidth={680}
      />

      <Modal opened={opened} onClose={close} title={t('masterdata.partners.new')}>
        <form onSubmit={submit}>
          <Stack>
            <TextInput label={t('field.code')} required {...form.getInputProps('code')} />
            <TextInput label={t('field.name')} required {...form.getInputProps('name')} />
            <Group grow>
              <Switch label={t('field.vendor')} {...form.getInputProps('vendor', { type: 'checkbox' })} />
              <Switch label={t('field.customer')} {...form.getInputProps('customer', { type: 'checkbox' })} />
            </Group>
            {form.errors.vendor && (
              <Text size="xs" c="red">
                {form.errors.vendor}
              </Text>
            )}
            <TextInput label={t('masterdata.partners.form.taxId')} {...form.getInputProps('taxId')} />
            <NumberInput
              label={t('masterdata.partners.form.paymentTerms')}
              min={0}
              {...form.getInputProps('paymentTermsDays')}
            />
            <Group grow>
              <TextInput
                label={t('masterdata.partners.form.apAccount')}
                {...form.getInputProps('apAccountCode')}
              />
              <TextInput
                label={t('masterdata.partners.form.arAccount')}
                {...form.getInputProps('arAccountCode')}
              />
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
    </Stack>
  );
}
