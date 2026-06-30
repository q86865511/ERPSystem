import { Alert, Badge, Button, Group, Stack, Table, Text, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import dayjs from 'dayjs';
import { useState } from 'react';
import { MoneyText } from '../../components/Money';
import { useAuth } from '../../auth/useAuth';
import { useI18n } from '../../i18n';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useJournalEntry, useReverseEntry } from './api';

export function ReversalPanel() {
  const { t } = useI18n();
  const { canDo } = useAuth();
  const [entryNoInput, setEntryNoInput] = useState('');
  const [loadedEntryNo, setLoadedEntryNo] = useState<number | null>(null);
  const [reversalDate, setReversalDate] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const [memo, setMemo] = useState('');
  const entry = useJournalEntry(loadedEntryNo);
  const reverse = useReverseEntry();

  if (!canDo('ledger.post')) {
    return (
      <Text c="dimmed" py="md">
        {t('ledger.reversal.requiresAccountant')}
      </Text>
    );
  }

  const d = entry.data;
  const reversible = d != null && d.sourceDocType == null && d.reversedByEntryNo == null;

  const load = () => {
    const n = Number(entryNoInput);
    if (Number.isInteger(n) && n > 0) {
      setLoadedEntryNo(n);
    }
  };

  const submit = async () => {
    if (loadedEntryNo == null) return;
    try {
      const result = await reverse.mutateAsync({
        entryNo: loadedEntryNo,
        reversalDate: reversalDate ?? undefined,
        memo: memo.trim() || undefined,
      });
      notifySuccess(t('ledger.reversal.done', { entryNo: result?.entryNo ?? '' }));
      setMemo('');
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <Stack maw={760}>
      <Text c="dimmed" size="sm">
        {t('ledger.reversal.hint')}
      </Text>

      <Group align="flex-end">
        <TextInput
          label={t('ledger.reversal.entryNo')}
          value={entryNoInput}
          onChange={(e) => setEntryNoInput(e.currentTarget.value)}
          w={160}
        />
        <Button variant="default" onClick={load} loading={entry.isFetching}>
          {t('ledger.reversal.load')}
        </Button>
      </Group>

      {entry.isError && (
        <Alert color="red">{t('ledger.reversal.notFound', { entryNo: loadedEntryNo ?? '' })}</Alert>
      )}

      {d && (
        <Stack>
          <Group>
            <Badge variant="light">#{d.entryNo}</Badge>
            <Text size="sm">{d.postingDate}</Text>
            {d.memo && (
              <Text size="sm" c="dimmed">
                {d.memo}
              </Text>
            )}
            <Badge variant="light" color="gray">
              {t('ledger.reversal.source')}: {d.sourceDocType ?? t('ledger.reversal.manual')}
            </Badge>
          </Group>

          {d.reversedByEntryNo != null && (
            <Alert color="orange">
              {t('ledger.reversal.alreadyReversed', { entryNo: d.reversedByEntryNo })}
            </Alert>
          )}
          {d.sourceDocType != null && d.reversedByEntryNo == null && (
            <Alert color="orange">{t('ledger.reversal.notReversible')}</Alert>
          )}

          <Table withTableBorder>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t('ledger.manual.account')}</Table.Th>
                <Table.Th ta="right">{t('field.debit')}</Table.Th>
                <Table.Th ta="right">{t('field.credit')}</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(d.lines ?? []).map((l, i) => (
                <Table.Tr key={i}>
                  <Table.Td>
                    {l.accountCode} — {l.accountName ?? ''}
                  </Table.Td>
                  <Table.Td ta="right">
                    <MoneyText value={l.debit} />
                  </Table.Td>
                  <Table.Td ta="right">
                    <MoneyText value={l.credit} />
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>

          <Group align="flex-end">
            <DateInput
              label={t('ledger.reversal.reversalDate')}
              value={reversalDate}
              onChange={(v) => setReversalDate(v)}
            />
            <TextInput
              label={t('field.memo')}
              value={memo}
              onChange={(e) => setMemo(e.currentTarget.value)}
            />
            <Button onClick={submit} loading={reverse.isPending} disabled={!reversible}>
              {t('ledger.reversal.reverse')}
            </Button>
          </Group>
        </Stack>
      )}
    </Stack>
  );
}
