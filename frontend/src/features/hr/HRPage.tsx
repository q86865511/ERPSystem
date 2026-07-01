import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { useTabParam } from '../../hooks/useTabParam';
import { DepartmentsPanel } from './DepartmentsPanel';
import { EmployeesPanel } from './EmployeesPanel';
import { HrDashboardPanel } from './HrDashboardPanel';
import { PositionsPanel } from './PositionsPanel';

export function HRPage() {
  const { t } = useI18n();
  const [tab, setTab] = useTabParam('dashboard');
  return (
    <>
      <PageHeader title={t('nav.humanResources')} subtitle={t('hr.subtitle')} />
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="dashboard">{t('hr.tabs.dashboard')}</Tabs.Tab>
          <Tabs.Tab value="employees">{t('hr.tabs.employees')}</Tabs.Tab>
          <Tabs.Tab value="departments">{t('hr.tabs.departments')}</Tabs.Tab>
          <Tabs.Tab value="positions">{t('hr.tabs.positions')}</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="dashboard">
          <HrDashboardPanel />
        </Tabs.Panel>
        <Tabs.Panel value="employees">
          <EmployeesPanel />
        </Tabs.Panel>
        <Tabs.Panel value="departments">
          <DepartmentsPanel />
        </Tabs.Panel>
        <Tabs.Panel value="positions">
          <PositionsPanel />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
