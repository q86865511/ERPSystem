import { useState } from 'react';
import { Alert, Button, Divider, Group, NumberInput, Stack, Text, TextInput } from '@mantine/core';
import dayjs from 'dayjs';
import type { FiscalPeriodResponse, YearEndCloseResult } from '../../api/types';
import { StatusBadge } from '../../components/StatusBadge';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useClosePeriod, useCloseYear, useReopenPeriod } from './api';

export function FiscalPeriodsPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const close = useClosePeriod();
  const reopen = useReopenPeriod();
  const closeYear = useCloseYear();
  const [yearCode, setYearCode] = useState(dayjs().format('YYYY'));
  const [periodNo, setPeriodNo] = useState<number | string>(dayjs().month() + 1);
  const [result, setResult] = useState<FiscalPeriodResponse | null>(null);
  const [yearEnd, setYearEnd] = useState<YearEndCloseResult | null>(null);

  if (!canDo('ledger.post')) {
    return (
      <Text c="dimmed" py="md">
        {t('ledger.periods.requiresAccountant')}
      </Text>
    );
  }

  const run = async (kind: 'close' | 'reopen') => {
    const args = { yearCode, periodNo: Number(periodNo) };
    try {
      const r = kind === 'close' ? await close.mutateAsync(args) : await reopen.mutateAsync(args);
      setResult(r ?? null);
      notifySuccess(
        kind === 'close'
          ? t('ledger.periods.closed', { periodNo })
          : t('ledger.periods.reopened', { periodNo }),
      );
    } catch (e) {
      notifyError(e);
    }
  };

  const runCloseYear = async () => {
    try {
      const r = await closeYear.mutateAsync(yearCode);
      setYearEnd(r ?? null);
      notifySuccess(t('ledger.periods.yearClosed', { yearCode }));
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <Stack maw={460}>
      <Text size="sm" c="dimmed">
        {t('ledger.periods.hint')}
      </Text>
      <Group grow>
        <TextInput label={t('ledger.periods.fiscalYearCode')} value={yearCode} onChange={(e) => setYearCode(e.currentTarget.value)} />
        <NumberInput label={t('ledger.periods.periodMonth')} min={1} max={12} value={periodNo} onChange={setPeriodNo} />
      </Group>
      <Group>
        <Button color="orange" loading={close.isPending} onClick={() => run('close')}>
          {t('ledger.periods.closePeriod')}
        </Button>
        <Button variant="default" loading={reopen.isPending} onClick={() => run('reopen')}>
          {t('ledger.periods.reopenPeriod')}
        </Button>
      </Group>

      {result && (
        <Alert
          variant="light"
          title={t('ledger.periods.alertTitle', {
            periodNo: result.periodNo ?? '',
            yearCode: result.yearCode ?? '',
          })}
        >
          <Group gap="xs">
            <StatusBadge status={result.status} />
            <Text size="sm" c="dimmed">
              {result.startDate} → {result.endDate}
            </Text>
          </Group>
        </Alert>
      )}

      <Divider label={t('ledger.periods.yearEndTitle')} labelPosition="left" mt="md" />
      <Text size="sm" c="dimmed">
        {t('ledger.periods.yearEndHint')}
      </Text>
      <Group>
        <Button color="red" loading={closeYear.isPending} onClick={runCloseYear}>
          {t('ledger.periods.closeYear', { yearCode })}
        </Button>
      </Group>

      {yearEnd && (
        <Alert variant="light" color="red" title={t('ledger.periods.yearClosed', { yearCode: yearEnd.yearCode ?? '' })}>
          <Stack gap={4}>
            <Group gap="xs">
              <Text size="sm" c="dimmed">
                {t('ledger.periods.netIncome')}
              </Text>
              <MoneyText value={yearEnd.netIncome} />
            </Group>
            <Text size="sm" c="dimmed">
              {yearEnd.closingEntryNo != null
                ? t('ledger.periods.closingEntry', { entryNo: yearEnd.closingEntryNo })
                : t('ledger.periods.noClosingEntry')}
              {' · '}
              {t('ledger.periods.periodsLocked', { count: yearEnd.periodsLocked ?? 0 })}
            </Text>
          </Stack>
        </Alert>
      )}
    </Stack>
  );
}
