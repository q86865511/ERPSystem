import { useState } from 'react';
import { Card, SimpleGrid, Stack, Text } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import dayjs from 'dayjs';
import { MoneyText } from '../../components/Money';
import { useI18n } from '../../i18n';
import { useApAging } from './api';

function Bucket({ label, value, strong }: { label: string; value?: string; strong?: boolean }) {
  return (
    <Card withBorder radius="md" padding="sm">
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Text fw={strong ? 700 : 600} ff="monospace">
        <MoneyText value={value} />
      </Text>
    </Card>
  );
}

export function ApAgingPanel() {
  const { t } = useI18n();
  const [asOf, setAsOf] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const { data } = useApAging(asOf ?? undefined);

  return (
    <Stack>
      <DateInput label={t('field.asOf')} value={asOf} onChange={setAsOf} maw={180} />
      <SimpleGrid cols={{ base: 2, sm: 3, md: 6 }}>
        <Bucket label={t('purchasing.aging.current')} value={data?.current} />
        <Bucket label={t('purchasing.aging.days1to30')} value={data?.days1to30} />
        <Bucket label={t('purchasing.aging.days31to60')} value={data?.days31to60} />
        <Bucket label={t('purchasing.aging.days61to90')} value={data?.days61to90} />
        <Bucket label={t('purchasing.aging.days90plus')} value={data?.days90plus} />
        <Bucket label={t('field.total')} value={data?.total} strong />
      </SimpleGrid>
    </Stack>
  );
}
