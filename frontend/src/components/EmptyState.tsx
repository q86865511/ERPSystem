import { Center, Stack, Text } from '@mantine/core';
import type { ReactNode } from 'react';

/** "No data" placeholder: a centered dimmed message with an optional call-to-action. */
export function EmptyState({ message, cta }: { message: string; cta?: ReactNode }) {
  return (
    <Center py="xl">
      <Stack align="center" gap="sm">
        <Text c="dimmed" ta="center">
          {message}
        </Text>
        {cta}
      </Stack>
    </Center>
  );
}
