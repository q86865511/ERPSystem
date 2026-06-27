import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { DateInput } from '@mantine/dates';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import dayjs from 'dayjs';
import type { JournalEntryRequest } from '../../api/types';
import { formatMoney, sumMoney } from '../../components/Money';
import { useTrialBalance } from '../reporting/api';
import { useAuth } from '../../auth/useAuth';
import { notifyError, notifySuccess } from '../../lib/notify';
import { useCreateJournalEntry } from './api';

type LineForm = { accountCode: string; debit: string; credit: string; memo: string };
const emptyLine = (): LineForm => ({ accountCode: '', debit: '', credit: '', memo: '' });

export function ManualEntryPanel() {
  const { canDo } = useAuth();
  const tb = useTrialBalance();
  const create = useCreateJournalEntry();

  const accountOptions = (tb.data?.lines ?? [])
    .filter((l) => l.code)
    .map((l) => ({ value: l.code as string, label: `${l.code} — ${l.name ?? ''}` }));

  const form = useForm<{ postingDate: string | null; memo: string; lines: LineForm[] }>({
    initialValues: {
      postingDate: dayjs().format('YYYY-MM-DD'),
      memo: '',
      lines: [emptyLine(), emptyLine()],
    },
  });

  if (!canDo('ledger.post')) {
    return (
      <Text c="dimmed" py="md">
        Manual journal entries require the ACCOUNTANT role.
      </Text>
    );
  }

  const totalDebit = sumMoney(form.values.lines.map((l) => l.debit));
  const totalCredit = sumMoney(form.values.lines.map((l) => l.credit));
  const balanced = totalDebit === totalCredit && Number(totalDebit) > 0;

  const submit = async () => {
    const lines = form.values.lines
      .filter((l) => l.accountCode && (l.debit || l.credit))
      .map((l) => ({
        accountCode: l.accountCode,
        debit: l.debit.trim() || undefined,
        credit: l.credit.trim() || undefined,
        memo: l.memo.trim() || undefined,
      }));
    if (!balanced || lines.length < 2) {
      notifyError('Entry must balance (total debit = total credit) with at least two lines');
      return;
    }
    const body: JournalEntryRequest = {
      postingDate: form.values.postingDate ?? undefined,
      memo: form.values.memo.trim() || undefined,
      lines,
    };
    try {
      const result = await create.mutateAsync(body);
      notifySuccess(`Journal entry #${result?.entryNo} posted`);
      form.reset();
    } catch (e) {
      notifyError(e);
    }
  };

  return (
    <Stack>
      <Group grow maw={520}>
        <DateInput label="Posting date" value={form.values.postingDate} onChange={(d) => form.setFieldValue('postingDate', d)} />
        <TextInput label="Memo" {...form.getInputProps('memo')} />
      </Group>

      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th w={260}>Account</Table.Th>
            <Table.Th w={140}>Debit</Table.Th>
            <Table.Th w={140}>Credit</Table.Th>
            <Table.Th>Memo</Table.Th>
            <Table.Th />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {form.values.lines.map((_, i) => (
            <Table.Tr key={i}>
              <Table.Td>
                <Select
                  searchable
                  placeholder="Account"
                  data={accountOptions}
                  value={form.values.lines[i]?.accountCode || null}
                  onChange={(v) => form.setFieldValue(`lines.${i}.accountCode`, v ?? '')}
                />
              </Table.Td>
              <Table.Td>
                <TextInput {...form.getInputProps(`lines.${i}.debit`)} />
              </Table.Td>
              <Table.Td>
                <TextInput {...form.getInputProps(`lines.${i}.credit`)} />
              </Table.Td>
              <Table.Td>
                <TextInput {...form.getInputProps(`lines.${i}.memo`)} />
              </Table.Td>
              <Table.Td>
                <ActionIcon
                  variant="subtle"
                  color="red"
                  disabled={form.values.lines.length <= 2}
                  onClick={() => form.removeListItem('lines', i)}
                >
                  <IconTrash size={16} />
                </ActionIcon>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      <Group justify="space-between">
        <Button
          size="xs"
          variant="default"
          leftSection={<IconPlus size={14} />}
          onClick={() => form.insertListItem('lines', emptyLine())}
        >
          Add line
        </Button>
        <Group>
          <Text size="sm">Debit {formatMoney(totalDebit)}</Text>
          <Text size="sm">Credit {formatMoney(totalCredit)}</Text>
          <Badge color={balanced ? 'teal' : 'red'} variant="light">
            {balanced ? 'Balanced' : 'Unbalanced'}
          </Badge>
        </Group>
      </Group>

      <Group justify="flex-end">
        <Button onClick={submit} loading={create.isPending} disabled={!balanced}>
          Post entry
        </Button>
      </Group>
    </Stack>
  );
}
