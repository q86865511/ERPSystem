import { useState } from 'react';
import { Card, SimpleGrid, Stack, Text } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import dayjs from 'dayjs';
import { MoneyText } from '../../components/Money';
import { useArAging } from './api';

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

export function ArAgingPanel() {
  const [asOf, setAsOf] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const { data } = useArAging(asOf ?? undefined);

  return (
    <Stack>
      <DateInput label="As of" value={asOf} onChange={setAsOf} maw={180} />
      <SimpleGrid cols={{ base: 2, sm: 3, md: 6 }}>
        <Bucket label="Current" value={data?.current} />
        <Bucket label="1–30" value={data?.days1to30} />
        <Bucket label="31–60" value={data?.days31to60} />
        <Bucket label="61–90" value={data?.days61to90} />
        <Bucket label="90+" value={data?.days90plus} />
        <Bucket label="Total" value={data?.total} strong />
      </SimpleGrid>
    </Stack>
  );
}
