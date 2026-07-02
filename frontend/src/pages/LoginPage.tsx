import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Center,
  Divider,
  Group,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertTriangle } from '@tabler/icons-react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { useI18n } from '../i18n';

const ROLE_USERS = ['admin', 'accountant', 'warehouse', 'sales', 'hr'];

export function LoginPage() {
  const { login, user } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const form = useForm({ initialValues: { username: '', password: '' } });
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/';

  if (user) {
    return <Navigate to={from} replace />;
  }

  const submit = async (username: string, password: string) => {
    setError(null);
    setLoading(true);
    try {
      await login(username, password);
      navigate(from, { replace: true });
    } catch {
      setError(t('login.invalidCredentials'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Center mih="100vh" p="md">
      <Card withBorder shadow="sm" radius="md" p="xl" w={400}>
        <Stack gap="md">
          <Stack gap={2}>
            <Title order={3}>{t('app.title')}</Title>
            <Text size="sm" c="dimmed">
              {t('login.subtitle')}
            </Text>
          </Stack>

          {error && (
            <Alert color="red" icon={<IconAlertTriangle size={16} />} variant="light">
              {error}
            </Alert>
          )}

          <form onSubmit={form.onSubmit((v) => submit(v.username, v.password))}>
            <Stack gap="sm">
              <TextInput
                label={t('login.username')}
                autoComplete="username"
                required
                {...form.getInputProps('username')}
              />
              <PasswordInput
                label={t('login.password')}
                autoComplete="current-password"
                required
                {...form.getInputProps('password')}
              />
              <Button type="submit" loading={loading} fullWidth mt="xs">
                {t('login.signIn')}
              </Button>
            </Stack>
          </form>

          <Stack gap="md" data-onboarding="demo-accounts">
            <Divider label={t('login.demoAccounts')} labelPosition="center" />
            <Button variant="light" disabled={loading} onClick={() => submit('guest', 'guest')}>
              {t('login.guestEntry')}
            </Button>
            <Group grow gap="xs">
              {ROLE_USERS.map((name) => (
                <Button
                  key={name}
                  size="xs"
                  variant="default"
                  disabled={loading}
                  onClick={() => submit(name, name)}
                >
                  {name}
                </Button>
              ))}
            </Group>
            <Text size="xs" c="dimmed" ta="center">
              {t('login.demoHint')}
            </Text>
          </Stack>
        </Stack>
      </Card>
    </Center>
  );
}
