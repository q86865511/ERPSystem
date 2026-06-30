import { useState } from 'react';
import { SimpleGrid, Stack } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import dayjs from 'dayjs';
import { StatTile } from '../../components/StatTile';
import { useI18n } from '../../i18n';
import { useArAging } from './api';

/** AR aging: as-of date + the backend's five aging buckets plus a total, as KPI tiles. */
export function ArAgingPanel() {
  const { t } = useI18n();
  const [asOf, setAsOf] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const { data } = useArAging(asOf ?? undefined);

  return (
    <Stack>
      <DateInput label={t('field.asOf')} value={asOf} onChange={setAsOf} maw={180} />
      <SimpleGrid cols={{ base: 2, sm: 3, md: 6 }}>
        <StatTile label={t('sales.arAging.current')} value={data?.current} />
        <StatTile label={t('sales.arAging.days1to30')} value={data?.days1to30} />
        <StatTile label={t('sales.arAging.days31to60')} value={data?.days31to60} />
        <StatTile label={t('sales.arAging.days61to90')} value={data?.days61to90} />
        <StatTile label={t('sales.arAging.days90plus')} value={data?.days90plus} valueColor="red" />
        <StatTile label={t('field.total')} value={data?.total} strong />
      </SimpleGrid>
    </Stack>
  );
}
