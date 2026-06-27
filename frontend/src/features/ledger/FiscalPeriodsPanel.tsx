import { useState } from 'react';
import { Alert, Button, Group, NumberInput, Stack, Text, TextInput } from '@mantine/core';
import dayjs from 'dayjs';
import type { FiscalPeriodResponse } from '../../api/types';
import { StatusBadge } from '../../components/StatusBadge';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useClosePeriod, useReopenPeriod } from './api';

export function FiscalPeriodsPanel() {
  const { canDo } = useAuth();
  const close = useClosePeriod();
  const reopen = useReopenPeriod();
  const [yearCode, setYearCode] = useState(dayjs().format('YYYY'));
  const [periodNo, setPeriodNo] = useState<number | string>(dayjs().month() + 1);
  const [result, setResult] = useState<FiscalPeriodResponse | null>(null);

  if (!canDo('ledger.post')) {
    return (
      <Text c="dimmed" py="md">
        Closing or reopening periods requires the ACCOUNTANT role.
      </Text>
    );
  }

  const run = async (kind: 'close' | 'reopen') => {
    const args = { yearCode, periodNo: Number(periodNo) };
    try {
      const r = kind === 'close' ? await close.mutateAsync(args) : await reopen.mutateAsync(args);
      setResult(r ?? null);
      notifySuccess(`Period ${periodNo} ${kind === 'close' ? 'closed' : 'reopened'}`);
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <Stack maw={460}>
      <Text size="sm" c="dimmed">
        Soft-close blocks postings dated in a closed period; reopen to allow them again.
      </Text>
      <Group grow>
        <TextInput label="Fiscal year code" value={yearCode} onChange={(e) => setYearCode(e.currentTarget.value)} />
        <NumberInput label="Period (month)" min={1} max={12} value={periodNo} onChange={setPeriodNo} />
      </Group>
      <Group>
        <Button color="orange" loading={close.isPending} onClick={() => run('close')}>
          Close period
        </Button>
        <Button variant="default" loading={reopen.isPending} onClick={() => run('reopen')}>
          Reopen period
        </Button>
      </Group>

      {result && (
        <Alert variant="light" title={`Period ${result.periodNo} · ${result.yearCode}`}>
          <Group gap="xs">
            <StatusBadge status={result.status} />
            <Text size="sm" c="dimmed">
              {result.startDate} → {result.endDate}
            </Text>
          </Group>
        </Alert>
      )}
    </Stack>
  );
}
