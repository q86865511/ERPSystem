import { useState } from 'react';
import {
  Badge,
  Center,
  Group,
  Loader,
  Pagination,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import dayjs from 'dayjs';
import { useDebouncedValue } from '@mantine/hooks';
import { useI18n } from '../../i18n';
import type { TranslationKey } from '../../i18n';
import { useAuditLog } from './api';

const EVENT_TYPES = [
  'JOURNAL_POSTED',
  'PERIOD_CLOSED',
  'PERIOD_REOPENED',
  'LOGIN_SUCCESS',
  'LOGIN_FAILURE',
] as const;

const EVENT_KEY: Record<string, TranslationKey> = {
  JOURNAL_POSTED: 'audit.event.JOURNAL_POSTED',
  PERIOD_CLOSED: 'audit.event.PERIOD_CLOSED',
  PERIOD_REOPENED: 'audit.event.PERIOD_REOPENED',
  LOGIN_SUCCESS: 'audit.event.LOGIN_SUCCESS',
  LOGIN_FAILURE: 'audit.event.LOGIN_FAILURE',
};

const EVENT_COLOR: Record<string, string> = {
  JOURNAL_POSTED: 'blue',
  PERIOD_CLOSED: 'orange',
  PERIOD_REOPENED: 'grape',
  LOGIN_SUCCESS: 'teal',
  LOGIN_FAILURE: 'red',
};

export function AuditPage() {
  const { t } = useI18n();
  const [eventType, setEventType] = useState<string | null>(null);
  const [actorInput, setActorInput] = useState('');
  const [actor] = useDebouncedValue(actorInput, 300);
  const [page, setPage] = useState(0);

  const query = useAuditLog(
    { eventType: eventType ?? undefined, actor: actor || undefined },
    page,
  );
  const data = query.data;

  function eventLabel(type?: string) {
    const key = type ? EVENT_KEY[type] : undefined;
    return key ? t(key) : (type ?? '');
  }

  return (
    <Stack>
      <div>
        <Title order={2}>{t('audit.title')}</Title>
        <Text c="dimmed" size="sm">
          {t('audit.subtitle')}
        </Text>
      </div>

      <Group align="flex-end">
        <Select
          label={t('audit.filter.event')}
          placeholder={t('audit.filter.all')}
          clearable
          value={eventType}
          onChange={(v) => {
            setEventType(v);
            setPage(0);
          }}
          data={EVENT_TYPES.map((type) => ({ value: type, label: eventLabel(type) }))}
          w={220}
        />
        <TextInput
          label={t('audit.filter.actor')}
          placeholder={t('audit.filter.actorPlaceholder')}
          value={actorInput}
          onChange={(e) => {
            setActorInput(e.currentTarget.value);
            setPage(0);
          }}
          w={220}
        />
        {data && (
          <Text c="dimmed" size="sm" pb={6}>
            {t('audit.total', { count: data.totalElements ?? 0 })}
          </Text>
        )}
      </Group>

      {query.isLoading ? (
        <Center py="xl">
          <Loader />
        </Center>
      ) : !data || (data.content ?? []).length === 0 ? (
        <Text c="dimmed">{t('audit.empty')}</Text>
      ) : (
        <Table striped highlightOnHover withTableBorder>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('audit.col.time')}</Table.Th>
              <Table.Th>{t('audit.col.event')}</Table.Th>
              <Table.Th>{t('audit.col.actor')}</Table.Th>
              <Table.Th>{t('audit.col.summary')}</Table.Th>
              <Table.Th>{t('audit.col.ref')}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {(data.content ?? []).map((row) => (
              <Table.Tr key={row.id}>
                <Table.Td style={{ whiteSpace: 'nowrap' }}>
                  {row.occurredAt ? dayjs(row.occurredAt).format('YYYY-MM-DD HH:mm:ss') : '—'}
                </Table.Td>
                <Table.Td>
                  <Badge color={row.eventType ? (EVENT_COLOR[row.eventType] ?? 'gray') : 'gray'} variant="light">
                    {eventLabel(row.eventType)}
                  </Badge>
                </Table.Td>
                <Table.Td>{row.actor}</Table.Td>
                <Table.Td>{row.summary}</Table.Td>
                <Table.Td c="dimmed">
                  {row.refType ? `${row.refType} ${row.refId ?? ''}`.trim() : '—'}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {data && (data.totalPages ?? 0) > 1 && (
        <Group justify="center">
          <Pagination
            total={data.totalPages ?? 1}
            value={page + 1}
            onChange={(p) => setPage(p - 1)}
          />
        </Group>
      )}
    </Stack>
  );
}
