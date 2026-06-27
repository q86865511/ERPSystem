import { Button, Container, Stack, Text, Title } from '@mantine/core';
import { useNavigate } from 'react-router-dom';

export function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <Container size="sm" py="xl">
      <Stack align="center" gap="sm">
        <Title order={1}>404</Title>
        <Text c="dimmed">That page doesn't exist.</Text>
        <Button variant="light" onClick={() => navigate('/')}>
          Back to dashboard
        </Button>
      </Stack>
    </Container>
  );
}
