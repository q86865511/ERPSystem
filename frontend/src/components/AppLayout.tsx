import { Suspense } from 'react';
import {
  AppShell,
  Avatar,
  Burger,
  Button,
  Center,
  Group,
  Loader,
  Menu,
  NavLink,
  ScrollArea,
  SegmentedControl,
  Title,
  useMantineColorScheme,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconBook2,
  IconBoxSeam,
  IconBuildingFactory2,
  IconDatabase,
  IconDeviceDesktop,
  IconHistory,
  IconLayoutDashboard,
  IconLogout,
  IconMoon,
  IconReportAnalytics,
  IconShoppingCart,
  IconSun,
  IconTruckDelivery,
} from '@tabler/icons-react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import type { Role } from '../auth/roles';
import { useI18n } from '../i18n';
import type { Locale, TranslationKey } from '../i18n';

const NAV: {
  to: string;
  labelKey: TranslationKey;
  icon: typeof IconLayoutDashboard;
  requiredRole?: Role;
}[] = [
  { to: '/', labelKey: 'nav.dashboard', icon: IconLayoutDashboard },
  { to: '/masterdata', labelKey: 'nav.masterData', icon: IconDatabase },
  { to: '/purchasing', labelKey: 'nav.purchasing', icon: IconShoppingCart },
  { to: '/sales', labelKey: 'nav.sales', icon: IconTruckDelivery },
  { to: '/manufacturing', labelKey: 'nav.manufacturing', icon: IconBuildingFactory2 },
  { to: '/inventory', labelKey: 'nav.inventory', icon: IconBoxSeam },
  { to: '/reporting', labelKey: 'nav.reporting', icon: IconReportAnalytics },
  { to: '/ledger', labelKey: 'nav.ledger', icon: IconBook2 },
  { to: '/audit', labelKey: 'nav.audit', icon: IconHistory, requiredRole: 'ADMIN' },
];

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  const location = useLocation();
  const { user, logout, hasRole } = useAuth();
  const { locale, setLocale, t } = useI18n();
  const { colorScheme, setColorScheme } = useMantineColorScheme();

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 260, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Title order={4}>{t('app.title')}</Title>
          </Group>
          <Group gap="sm">
            <SegmentedControl
              size="xs"
              aria-label={t('common.colorScheme')}
              value={colorScheme}
              onChange={(v) => setColorScheme(v as 'light' | 'dark' | 'auto')}
              data={[
                { value: 'light', label: <IconSun size={15} role="img" aria-label={t('theme.light')} /> },
                { value: 'dark', label: <IconMoon size={15} role="img" aria-label={t('theme.dark')} /> },
                { value: 'auto', label: <IconDeviceDesktop size={15} role="img" aria-label={t('theme.auto')} /> },
              ]}
            />
            <SegmentedControl
              size="xs"
              aria-label={t('common.language')}
              value={locale}
              onChange={(v) => setLocale(v as Locale)}
              data={[
                { label: '中', value: 'zh-TW' },
                { label: 'EN', value: 'en' },
              ]}
            />
            <Menu position="bottom-end" withinPortal>
              <Menu.Target>
                <Button
                  variant="subtle"
                  color="gray"
                  leftSection={
                    <Avatar size={26} radius="xl" color="terracotta">
                      {user?.username?.charAt(0).toUpperCase()}
                    </Avatar>
                  }
                >
                  {user?.username}
                </Button>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>{user?.roles.join(', ') || t('common.noRoles')}</Menu.Label>
                <Menu.Item color="red" leftSection={<IconLogout size={16} />} onClick={logout}>
                  {t('common.signOut')}
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        <ScrollArea>
          {NAV.filter((item) => !item.requiredRole || hasRole(item.requiredRole)).map((item) => {
            const active =
              item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to);
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                component={Link}
                to={item.to}
                label={t(item.labelKey)}
                leftSection={<Icon size={18} />}
                active={active}
                onClick={close}
              />
            );
          })}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main>
        <Suspense
          fallback={
            <Center py="xl">
              <Loader color="terracotta" />
            </Center>
          }
        >
          <Outlet />
        </Suspense>
      </AppShell.Main>
    </AppShell>
  );
}
