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

const DEMO_USERS = ['admin', 'accountant', 'warehouse', 'sales'];

export function LoginPage() {
  const { login, user } = useAuth();
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
      setError('Invalid username or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Center mih="100vh" p="md">
      <Card withBorder shadow="sm" radius="md" p="xl" w={400}>
        <Stack gap="md">
          <Stack gap={2}>
            <Title order={3}>Manufacturing ERP</Title>
            <Text size="sm" c="dimmed">
              Sign in to continue
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
                label="Username"
                autoComplete="username"
                required
                {...form.getInputProps('username')}
              />
              <PasswordInput
                label="Password"
                autoComplete="current-password"
                required
                {...form.getInputProps('password')}
              />
              <Button type="submit" loading={loading} fullWidth mt="xs">
                Sign in
              </Button>
            </Stack>
          </form>

          <Divider label="Demo accounts" labelPosition="center" />
          <Group grow gap="xs">
            {DEMO_USERS.map((name) => (
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
            Each demo account's password equals its username.
          </Text>
        </Stack>
      </Card>
    </Center>
  );
}
