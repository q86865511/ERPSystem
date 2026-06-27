import { AppShell, Avatar, Burger, Button, Group, Menu, NavLink, ScrollArea, Title } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconBook2,
  IconBoxSeam,
  IconBuildingFactory2,
  IconDatabase,
  IconLayoutDashboard,
  IconLogout,
  IconReportAnalytics,
  IconShoppingCart,
  IconTruckDelivery,
} from '@tabler/icons-react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const NAV = [
  { to: '/', label: 'Dashboard', icon: IconLayoutDashboard },
  { to: '/masterdata', label: 'Master Data', icon: IconDatabase },
  { to: '/purchasing', label: 'Purchasing', icon: IconShoppingCart },
  { to: '/sales', label: 'Sales', icon: IconTruckDelivery },
  { to: '/manufacturing', label: 'Manufacturing', icon: IconBuildingFactory2 },
  { to: '/inventory', label: 'Inventory', icon: IconBoxSeam },
  { to: '/reporting', label: 'Reporting', icon: IconReportAnalytics },
  { to: '/ledger', label: 'Ledger', icon: IconBook2 },
];

export function AppLayout() {
  const [opened, { toggle, close }] = useDisclosure();
  const location = useLocation();
  const { user, logout } = useAuth();

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
            <Title order={4}>Manufacturing ERP</Title>
          </Group>
          <Menu position="bottom-end" withinPortal>
            <Menu.Target>
              <Button
                variant="subtle"
                color="gray"
                leftSection={
                  <Avatar size={26} radius="xl" color="indigo">
                    {user?.username?.charAt(0).toUpperCase()}
                  </Avatar>
                }
              >
                {user?.username}
              </Button>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Label>{user?.roles.join(', ') || 'No roles'}</Menu.Label>
              <Menu.Item color="red" leftSection={<IconLogout size={16} />} onClick={logout}>
                Sign out
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        <ScrollArea>
          {NAV.map((item) => {
            const active =
              item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to);
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                component={Link}
                to={item.to}
                label={item.label}
                leftSection={<Icon size={18} />}
                active={active}
                onClick={close}
              />
            );
          })}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
