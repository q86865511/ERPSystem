import { Card, Group, Stack, Text } from '@mantine/core';
import { DonutChart } from '@mantine/charts';
import type { ReactNode } from 'react';
import { formatMoney } from '../Money';
import { moneyToNumber } from './palette';

export interface DonutDatum {
  name: string;
  amount: string | undefined;
  color: string;
}

/**
 * A titled card wrapping a donut chart + a money legend. Segment sizes come from `moneyToNumber` (viz
 * only); legend amounts use `formatMoney` (exact). Renders a dash when every segment is zero.
 */
export function DonutCard({
  title,
  badge,
  data,
  centerLabel,
}: {
  title: string;
  badge?: ReactNode;
  data: DonutDatum[];
  centerLabel?: string;
}) {
  const chartData = data.map((d) => ({ name: d.name, value: Math.max(0, moneyToNumber(d.amount)), color: d.color }));
  const allZero = chartData.every((d) => d.value === 0);

  return (
    <Card withBorder padding="md">
      <Group justify="space-between" mb="sm" wrap="nowrap">
        <Text fw={500}>{title}</Text>
        {badge}
      </Group>
      <Group wrap="nowrap" align="center" gap="lg">
        {allZero ? (
          <div style={{ width: 150, height: 150, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--mantine-color-dimmed)' }}>
            —
          </div>
        ) : (
          <DonutChart data={chartData} size={150} thickness={24} withTooltip tooltipDataSource="segment" chartLabel={centerLabel} />
        )}
        <Stack gap={6} style={{ flex: 1, minWidth: 0 }}>
          {data.map((d) => (
            <Group key={d.name} justify="space-between" gap="xs" wrap="nowrap">
              <Group gap={6} wrap="nowrap">
                <span style={{ width: 9, height: 9, borderRadius: 2, background: d.color, flex: 'none' }} />
                <Text size="xs" c="dimmed">
                  {d.name}
                </Text>
              </Group>
              <Text size="xs" ff="monospace">
                {formatMoney(d.amount)}
              </Text>
            </Group>
          ))}
        </Stack>
      </Group>
    </Card>
  );
}
