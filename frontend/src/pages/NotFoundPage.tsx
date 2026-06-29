import { Button, Container, Stack, Text, Title } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../i18n';

export function NotFoundPage() {
  const navigate = useNavigate();
  const { t } = useI18n();
  return (
    <Container size="sm" py="xl">
      <Stack align="center" gap="sm">
        <Title order={1}>404</Title>
        <Text c="dimmed">{t('notFound.message')}</Text>
        <Button variant="light" onClick={() => navigate('/')}>
          {t('common.backToDashboard')}
        </Button>
      </Stack>
    </Container>
  );
}
