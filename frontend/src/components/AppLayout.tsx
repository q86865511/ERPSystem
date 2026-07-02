import { Suspense, useState } from 'react';
import {
  ActionIcon,
  AppShell,
  Avatar,
  Burger,
  Button,
  Center,
  Collapse,
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
  IconChevronRight,
  IconLogout,
  IconMoon,
  IconReportAnalytics,
  IconShoppingCart,
  IconSun,
  IconTruckDelivery,
  IconUsers,
} from '@tabler/icons-react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import classes from './AppLayout.module.css';
import { useAuth } from '../auth/useAuth';
import type { Role } from '../auth/roles';
import { useI18n } from '../i18n';
import type { Locale, TranslationKey } from '../i18n';
import { useOnboardingTour } from '../onboarding/useOnboardingTour';

/** A sidebar sub-item: `value` is the module page's tab id (deep-linked as `?tab=`); `labelKey` reuses the
 *  module's existing tab i18n key. */
interface NavChild {
  value: string;
  labelKey: TranslationKey;
}

const NAV: {
  to: string;
  labelKey: TranslationKey;
  icon: typeof IconLayoutDashboard;
  requiredRole?: Role;
  onboardingId?: string;
  children?: NavChild[];
}[] = [
  { to: '/', labelKey: 'nav.dashboard', icon: IconLayoutDashboard },
  {
    to: '/masterdata',
    labelKey: 'nav.masterData',
    icon: IconDatabase,
    children: [
      { value: 'items', labelKey: 'masterdata.tabs.items' },
      { value: 'partners', labelKey: 'masterdata.tabs.partners' },
      { value: 'warehouses', labelKey: 'masterdata.tabs.warehouses' },
      { value: 'locations', labelKey: 'masterdata.tabs.locations' },
    ],
  },
  {
    to: '/purchasing',
    labelKey: 'nav.purchasing',
    icon: IconShoppingCart,
    onboardingId: 'nav-purchasing',
    children: [
      { value: 'orders', labelKey: 'purchasing.tabs.orders' },
      { value: 'receipts', labelKey: 'purchasing.tabs.receipts' },
      { value: 'bills', labelKey: 'purchasing.tabs.bills' },
      { value: 'payments', labelKey: 'purchasing.tabs.payments' },
      { value: 'ap-aging', labelKey: 'purchasing.tabs.apAging' },
    ],
  },
  {
    to: '/sales',
    labelKey: 'nav.sales',
    icon: IconTruckDelivery,
    children: [
      { value: 'orders', labelKey: 'sales.tabs.orders' },
      { value: 'deliveries', labelKey: 'sales.tabs.deliveries' },
      { value: 'invoices', labelKey: 'sales.tabs.invoices' },
      { value: 'receipts', labelKey: 'sales.tabs.receipts' },
      { value: 'returns', labelKey: 'sales.tabs.returns' },
      { value: 'ar-aging', labelKey: 'sales.tabs.arAging' },
    ],
  },
  {
    to: '/manufacturing',
    labelKey: 'nav.manufacturing',
    icon: IconBuildingFactory2,
    children: [
      { value: 'dashboard', labelKey: 'manufacturing.tabs.dashboard' },
      { value: 'work-orders', labelKey: 'manufacturing.tabs.workOrders' },
      { value: 'boms', labelKey: 'manufacturing.tabs.boms' },
      { value: 'reorder', labelKey: 'manufacturing.tabs.reorder' },
    ],
  },
  {
    to: '/inventory',
    labelKey: 'nav.inventory',
    icon: IconBoxSeam,
    children: [
      { value: 'dashboard', labelKey: 'inventory.tabs.dashboard' },
      { value: 'overview', labelKey: 'inventory.tabs.overview' },
      { value: 'adjustments', labelKey: 'inventory.tabs.adjustments' },
    ],
  },
  {
    to: '/reporting',
    labelKey: 'nav.reporting',
    icon: IconReportAnalytics,
    children: [
      { value: 'overview', labelKey: 'reporting.overview.tab' },
      { value: 'trial-balance', labelKey: 'reporting.tabs.trialBalance' },
      { value: 'income-statement', labelKey: 'reporting.tabs.incomeStatement' },
      { value: 'balance-sheet', labelKey: 'reporting.tabs.balanceSheet' },
    ],
  },
  {
    to: '/ledger',
    labelKey: 'nav.ledger',
    icon: IconBook2,
    children: [
      { value: 'manual-entry', labelKey: 'ledger.tabs.manualEntry' },
      { value: 'reversal', labelKey: 'ledger.tabs.reversal' },
      { value: 'periods', labelKey: 'ledger.tabs.periods' },
    ],
  },
  {
    to: '/hr',
    labelKey: 'nav.humanResources',
    icon: IconUsers,
    children: [
      { value: 'dashboard', labelKey: 'hr.tabs.dashboard' },
      { value: 'employees', labelKey: 'hr.tabs.employees' },
      { value: 'departments', labelKey: 'hr.tabs.departments' },
      { value: 'positions', labelKey: 'hr.tabs.positions' },
      { value: 'attendance', labelKey: 'hr.tabs.attendance' },
      { value: 'leave', labelKey: 'hr.tabs.leave' },
      { value: 'timesheets', labelKey: 'hr.tabs.timesheets' },
      { value: 'payroll', labelKey: 'hr.tabs.payroll' },
    ],
  },
  { to: '/audit', labelKey: 'nav.audit', icon: IconHistory, requiredRole: 'ADMIN' },
];

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  // Per-section expand overrides keyed by route. Defaults to the route-active module (see `open` below),
  // but the chevron lets users expand a non-active module or collapse the active one.
  const [openSections, setOpenSections] = useState<Record<string, boolean>>({});
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
                // Scheme-aware brand: brand-6 in light, lighter brand-4 in dark.
                color: 'var(--mantine-color-brand-text)',
              }}
            >
              {/* Ledger-balance mark; `currentColor` inherits the brand link color above. */}
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
                    <Avatar size={26} radius="xl" color="brand">
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
            if (!item.children) {
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
            }
            // The current `?tab=` (or the first child when none) is the active sub-item.
            const currentTab = new URLSearchParams(location.search).get('tab');
            const activeTab = currentTab ?? item.children[0]?.value;
            // Section is expanded when the user opened it, else it follows the active route. Two hit-areas:
            // the label (a real link) navigates + expands; the chevron only toggles expansion (ERP-001).
            const open = openSections[item.to] ?? active;
            return (
              <div key={item.to}>
                <div className={classes.parentRow} data-active={active || undefined}>
                  <NavLink
                    className={classes.parentLink}
                    component={Link}
                    to={item.to}
                    label={t(item.labelKey)}
                    leftSection={<Icon size={18} />}
                    active={active}
                    variant="subtle"
                    onClick={() => {
                      setOpenSections((s) => ({ ...s, [item.to]: true }));
                      close();
                    }}
                    data-onboarding={item.onboardingId}
                  />
                  <ActionIcon
                    className={classes.chevron}
                    variant="subtle"
                    color="gray"
                    data-open={open || undefined}
                    aria-expanded={open}
                    aria-label={t('nav.toggleSection', { module: t(item.labelKey) })}
                    onClick={(e) => {
                      // Toggle expansion only — never navigate, never close the mobile drawer.
                      e.preventDefault();
                      e.stopPropagation();
                      setOpenSections((s) => ({ ...s, [item.to]: !open }));
                    }}
                  >
                    <IconChevronRight size={16} />
                  </ActionIcon>
                </div>
                <Collapse expanded={open}>
                  <div className={classes.children}>
                    {item.children.map((child) => (
                      <NavLink
                        key={child.value}
                        component={Link}
                        to={`${item.to}?tab=${child.value}`}
                        label={t(child.labelKey)}
                        active={active && activeTab === child.value}
                        onClick={close}
                      />
                    ))}
                  </div>
                </Collapse>
              </div>
            );
          })}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main>
        <Suspense
          fallback={
            <Center py="xl">
              <Loader color="brand" />
            </Center>
          }
        >
          <Outlet />
        </Suspense>
      </AppShell.Main>
    </AppShell>
  );
}
