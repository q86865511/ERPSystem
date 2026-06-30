import { Card, Group, Text } from '@mantine/core';
import type { MantineColor } from '@mantine/core';
import type { ReactNode } from 'react';
import { MoneyText } from './Money';

/**
 * KPI tile: a dimmed label over a prominent value. Money values (the default) render monospace via
 * `MoneyText`; pass `money={false}` for a plain string. Optional leading `icon` and a top-right `status`
 * slot. Extracted from the dashboard summary cards so every module shows KPIs consistently.
 */
export function StatTile({
  label,
  value,
  money = true,
  icon,
  status,
  strong = false,
  valueColor,
}: {
  label: string;
  value?: string;
  money?: boolean;
  icon?: ReactNode;
  status?: ReactNode;
  strong?: boolean;
  /** Optional sentiment color for the value (e.g. `red` for an overdue aging bucket). */
  valueColor?: MantineColor;
}) {
  return (
    <Card withBorder padding="md">
      <Group justify="space-between" gap="xs" wrap="nowrap">
        <Group gap="xs" wrap="nowrap">
          {icon}
          <Text size="sm" c="dimmed">
            {label}
          </Text>
        </Group>
        {status}
      </Group>
      <Text fw={strong ? 700 : 600} fz="xl" ff="monospace" mt={4} c={valueColor}>
        {money ? <MoneyText value={value} /> : (value ?? '—')}
      </Text>
    </Card>
  );
}
