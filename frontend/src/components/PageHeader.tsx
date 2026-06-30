import { Group, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';

/**
 * Standard page header: optional breadcrumb + title (with an optional status badge beside it) + optional
 * subtitle on the left, actions on the right. `breadcrumb` and `status` are additive — existing callers
 * that pass only title/subtitle/actions are unaffected.
 */
export function PageHeader({
  title,
  subtitle,
  breadcrumb,
  status,
  actions,
}: {
  title: string;
  subtitle?: string;
  breadcrumb?: ReactNode;
  status?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <Group justify="space-between" align="flex-end" mb="md">
      <Stack gap={2}>
        {breadcrumb}
        <Group gap="sm" align="center">
          <Title order={2}>{title}</Title>
          {status}
        </Group>
        {subtitle && (
          <Text size="sm" c="dimmed">
            {subtitle}
          </Text>
        )}
      </Stack>
      {actions}
    </Group>
  );
}
