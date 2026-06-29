import { Button, Container, Stack, Text, Title } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '../i18n';

export function ForbiddenPage() {
  const navigate = useNavigate();
  const { t } = useI18n();
  return (
    <Container size="sm" py="xl">
      <Stack align="center" gap="sm">
        <Title order={1}>403</Title>
        <Text c="dimmed">{t('forbidden.message')}</Text>
        <Button variant="light" onClick={() => navigate('/')}>
          {t('common.backToDashboard')}
        </Button>
      </Stack>
    </Container>
  );
}
