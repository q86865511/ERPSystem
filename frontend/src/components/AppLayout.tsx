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
  IconHelpCircle,
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
import { useOnboardingTour } from '../onboarding/useOnboardingTour';

const NAV: {
  to: string;
  labelKey: TranslationKey;
  icon: typeof IconLayoutDashboard;
  requiredRole?: Role;
  onboardingId?: string;
}[] = [
  { to: '/', labelKey: 'nav.dashboard', icon: IconLayoutDashboard },
  { to: '/masterdata', labelKey: 'nav.masterData', icon: IconDatabase },
  { to: '/purchasing', labelKey: 'nav.purchasing', icon: IconShoppingCart, onboardingId: 'nav-purchasing' },
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
  const { restart: restartTour } = useOnboardingTour();

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
            <Link
              to="/"
              aria-label={t('app.title')}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                textDecoration: 'none',
                // Scheme-aware terracotta: terracotta-6 in light, lighter terracotta-4 in dark.
                color: 'var(--mantine-color-terracotta-text)',
              }}
            >
              {/* Ledger-balance mark; `currentColor` inherits the terracotta link color above. */}
              <svg
                width={28}
                height={28}
                viewBox="0 0 64 64"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden
                style={{ flexShrink: 0 }}
              >
                <path d="M 32 22 L 28 28 L 36 28 Z" fill="currentColor" />
                <rect x="12" y="24" width="16" height="3" rx="1.5" fill="currentColor" opacity="0.9" />
                <circle cx="15" cy="25.5" r="1.5" fill="currentColor" opacity="0.7" />
                <rect x="36" y="26" width="16" height="3" rx="1.5" fill="currentColor" opacity="0.85" />
                <circle cx="49" cy="27.5" r="1.5" fill="currentColor" opacity="0.7" />
                <line x1="12" y1="38" x2="52" y2="38" stroke="currentColor" strokeWidth={1.5} opacity="0.6" />
                <line x1="12" y1="42" x2="52" y2="42" stroke="currentColor" strokeWidth={1.5} opacity="0.5" />
              </svg>
              <Title order={4} style={{ margin: 0 }}>
                {t('app.title')}
              </Title>
            </Link>
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
              data-onboarding="header-language"
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
                <Menu.Item leftSection={<IconHelpCircle size={16} />} onClick={restartTour}>
                  {t('onboarding.restartTour')}
                </Menu.Item>
                <Menu.Divider />
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
                data-onboarding={item.onboardingId}
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
