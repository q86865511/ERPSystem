import { Card, Stack, Text, Title } from '@mantine/core';

/** Generic stand-in for module screens filled in by later stages, so the shell and nav work today. */
export function PlaceholderPage({ title, description }: { title: string; description?: string }) {
  return (
    <Stack gap="md">
      <Title order={2}>{title}</Title>
      <Card withBorder padding="xl" radius="md">
        <Text c="dimmed">{description ?? 'This screen will be implemented in an upcoming stage.'}</Text>
      </Card>
    </Stack>
  );
}
