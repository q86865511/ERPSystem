import { useState } from 'react';
import { Alert, Badge, Button, Group, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { DateInput } from '@mantine/dates';
import dayjs from 'dayjs';
import type { AdjustmentResponse, CreateAdjustmentRequest } from '../../api/types';
import { ItemSelect, LocationSelect } from '../../components/EntitySelect';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateAdjustment } from './api';

export function AdjustmentsPanel() {
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
      itemId: (v) => (v != null ? null : 'Pick an item'),
      locationId: (v) => (v != null ? null : 'Pick a location'),
      qtyDelta: (v) => (v ? null : 'Required (use a negative value to write down)'),
    },
  });

  if (!canDo('inventory.adjust')) {
    return (
      <Text c="dimmed" py="md">
        Stock adjustments require the WAREHOUSE role.
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
      notifySuccess(`Adjustment ${result?.adjustmentNumber} posted`);
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
            label="Item"
            value={form.values.itemId}
            onChange={(id) => form.setFieldValue('itemId', id)}
            error={form.errors.itemId}
          />
          <LocationSelect
            label="Location"
            locationType="STOCK"
            value={form.values.locationId}
            onChange={(id) => form.setFieldValue('locationId', id)}
            error={form.errors.locationId}
          />
          <Group grow>
            <TextInput label="Quantity delta" placeholder="e.g. 10 or -5" {...form.getInputProps('qtyDelta')} />
            <TextInput label="Unit cost" placeholder="for write-ups" {...form.getInputProps('unitCost')} />
          </Group>
          <TextInput label="Reason" {...form.getInputProps('reason')} />
          <DateInput label="Posting date" value={form.values.postingDate} onChange={(d) => form.setFieldValue('postingDate', d)} />
          <Group justify="flex-end">
            <Button type="submit" loading={create.isPending}>
              Post adjustment
            </Button>
          </Group>
        </Stack>
      </form>

      {last && (
        <Alert color="teal" variant="light" title={`Posted ${last.adjustmentNumber}`}>
          <Group gap="xs">
            <Text size="sm">qty {last.qtyDelta}</Text>
            {last.journalEntryId != null && <Badge variant="light">JE #{last.journalEntryId}</Badge>}
          </Group>
        </Alert>
      )}
    </Stack>
  );
}
