import { Group, Stack, Text, Title } from '@mantine/core';
import type { ReactNode } from 'react';

/** Standard page header: title + optional subtitle on the left, actions on the right. */
export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <Group justify="space-between" align="flex-end" mb="md">
      <Stack gap={2}>
        <Title order={2}>{title}</Title>
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
