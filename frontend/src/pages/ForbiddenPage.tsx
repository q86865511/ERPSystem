import { Button, Container, Stack, Text, Title } from '@mantine/core';
import { useNavigate } from 'react-router-dom';

export function ForbiddenPage() {
  const navigate = useNavigate();
  return (
    <Container size="sm" py="xl">
      <Stack align="center" gap="sm">
        <Title order={1}>403</Title>
        <Text c="dimmed">You don't have the role required for this action.</Text>
        <Button variant="light" onClick={() => navigate('/')}>
          Back to dashboard
        </Button>
      </Stack>
    </Container>
  );
}
