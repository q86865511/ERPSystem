import { useState } from 'react';
import { Alert, Badge, Button, Group, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { DateInput } from '@mantine/dates';
import dayjs from 'dayjs';
import type { AdjustmentResponse, CreateAdjustmentRequest } from '../../api/types';
import { ItemSelect, LocationSelect } from '../../components/EntitySelect';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateAdjustment } from './api';

export function AdjustmentsPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const create = useCreateAdjustment();
  const [last, setLast] = useState<AdjustmentResponse | null>(null);

  const form = useForm<{
    itemId: number | null;
    locationId: number | null;
    qtyDelta: string;
    unitCost: string;
    reason: string;
    postingDate: string | null;
  }>({
    initialValues: {
      itemId: null,
      locationId: null,
      qtyDelta: '',
      unitCost: '',
      reason: '',
      postingDate: dayjs().format('YYYY-MM-DD'),
    },
    validate: {
      itemId: (v) => (v != null ? null : t('inventory.errPickItem')),
      locationId: (v) => (v != null ? null : t('inventory.errPickLocation')),
      qtyDelta: (v) => (v ? null : t('inventory.errQtyDeltaRequired')),
    },
  });

  if (!canDo('inventory.adjust')) {
    return (
      <Text c="dimmed" py="md">
        {t('inventory.requiresWarehouseRole')}
      </Text>
    );
  }

  const submit = form.onSubmit(async (v) => {
    const body: CreateAdjustmentRequest = {
      itemId: v.itemId ?? undefined,
      locationId: v.locationId ?? undefined,
      qtyDelta: v.qtyDelta,
      unitCost: v.unitCost.trim() || undefined,
      reason: v.reason.trim() || undefined,
      postingDate: v.postingDate ?? undefined,
    };
    try {
      const result = await create.mutateAsync(body);
      setLast(result ?? null);
      notifySuccess(t('inventory.adjustmentPosted', { adjustmentNumber: result?.adjustmentNumber ?? '' }));
      form.setValues({ qtyDelta: '', unitCost: '', reason: '' });
    } catch (e) {
      notifyError(e);
    }
  });

  return (
    <Stack maw={520}>
      <form onSubmit={submit}>
        <Stack>
          <ItemSelect
            label={t('field.item')}
            value={form.values.itemId}
            onChange={(id) => form.setFieldValue('itemId', id)}
            error={form.errors.itemId}
          />
          <LocationSelect
            label={t('field.location')}
            locationType="STOCK"
            value={form.values.locationId}
            onChange={(id) => form.setFieldValue('locationId', id)}
            error={form.errors.locationId}
          />
          <Group grow>
            <TextInput label={t('inventory.quantityDelta')} placeholder={t('inventory.qtyDeltaPlaceholder')} {...form.getInputProps('qtyDelta')} />
            <TextInput label={t('field.unitCost')} placeholder={t('inventory.unitCostPlaceholder')} {...form.getInputProps('unitCost')} />
          </Group>
          <TextInput label={t('field.reason')} {...form.getInputProps('reason')} />
          <DateInput label={t('inventory.postingDate')} value={form.values.postingDate} onChange={(d) => form.setFieldValue('postingDate', d)} />
          <Group justify="flex-end">
            <Button type="submit" loading={create.isPending}>
              {t('inventory.postAdjustment')}
            </Button>
          </Group>
        </Stack>
      </form>

      {last && (
        <Alert color="teal" variant="light" title={t('inventory.postedTitle', { adjustmentNumber: last.adjustmentNumber ?? '' })}>
          <Group gap="xs">
            <Text size="sm">{t('inventory.qtyLabel', { qtyDelta: last.qtyDelta ?? '' })}</Text>
            {last.journalEntryId != null && <Badge variant="light">{t('inventory.je', { id: last.journalEntryId })}</Badge>}
          </Group>
        </Alert>
      )}
    </Stack>
  );
}
