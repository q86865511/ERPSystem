import { Box, Card, Group, Text } from '@mantine/core';
import { Sparkline } from '@mantine/charts';
import { IconArrowDownRight, IconArrowUpRight } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { MoneyText } from '../Money';

export interface KpiTileProps {
  label: string;
  value?: string;
  money?: boolean;
  /** Leading icon rendered inside a brand-tinted chip. */
  icon?: ReactNode;
  /** Period-over-period delta in percent; its sign shows direction. */
  delta?: number;
  /** When true, a negative delta is "good" (e.g. receivables going down) → shown green. */
  invertDelta?: boolean;
  /** Small trend series (chart sizing only). */
  spark?: number[];
  /** Top-right slot (e.g. a live/planned badge). */
  status?: ReactNode;
  /** Optional sentiment color for the value (e.g. red for a negative net income). */
  valueColor?: string;
}

/**
 * KPI magnet for the dashboards: brand-chip icon, label, prominent money value, and an optional
 * period-over-period delta + sparkline. Deltas/sparklines are only shown when real data is supplied — no
 * fabricated trends.
 */
export function KpiTile({ label, value, money = true, icon, delta, invertDelta, spark, status, valueColor }: KpiTileProps) {
  const hasDelta = typeof delta === 'number';
  const up = hasDelta && delta >= 0;
  const good = hasDelta ? (invertDelta ? !up : up) : false;
  const deltaColor = good ? 'var(--mantine-color-teal-7)' : 'var(--mantine-color-red-7)';
  const sparkColor = good ? 'teal.6' : 'red.6';

  return (
    <Card withBorder padding="md">
      <Group justify="space-between" mb={6} wrap="nowrap">
        {icon ? (
          <Box
            style={{
              width: 30,
              height: 30,
              borderRadius: 8,
              background: 'var(--mantine-color-brand-light)',
              color: 'var(--mantine-color-brand-light-color)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {icon}
          </Box>
        ) : (
          <span />
        )}
        {status}
      </Group>
      <Text size="sm" c="dimmed">
        {label}
      </Text>
      <Text fw={600} fz="xl" ff="monospace" mt={2} c={valueColor}>
        {money ? <MoneyText value={value} /> : (value ?? '—')}
      </Text>
      {(hasDelta || (spark && spark.length > 1)) && (
        <Group justify="space-between" mt={8} wrap="nowrap">
          {hasDelta ? (
            <Group gap={2} wrap="nowrap" style={{ color: deltaColor, fontSize: 13 }}>
              {up ? <IconArrowUpRight size={14} /> : <IconArrowDownRight size={14} />}
              {Math.abs(delta).toFixed(1)}%
            </Group>
          ) : (
            <span />
          )}
          {spark && spark.length > 1 ? (
            <Sparkline w={72} h={22} data={spark} color={sparkColor} fillOpacity={0.15} strokeWidth={1.5} curveType="natural" />
          ) : null}
        </Group>
      )}
    </Card>
  );
}
