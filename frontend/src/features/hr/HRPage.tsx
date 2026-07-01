import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { useTabParam } from '../../hooks/useTabParam';
import { AttendancePanel } from './AttendancePanel';
import { DepartmentsPanel } from './DepartmentsPanel';
import { EmployeesPanel } from './EmployeesPanel';
import { HrDashboardPanel } from './HrDashboardPanel';
import { LeavePanel } from './LeavePanel';
import { PayrollPanel } from './PayrollPanel';
import { PositionsPanel } from './PositionsPanel';
import { TimesheetPanel } from './TimesheetPanel';

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
          <Tabs.Tab value="attendance">{t('hr.tabs.attendance')}</Tabs.Tab>
          <Tabs.Tab value="leave">{t('hr.tabs.leave')}</Tabs.Tab>
          <Tabs.Tab value="timesheets">{t('hr.tabs.timesheets')}</Tabs.Tab>
          <Tabs.Tab value="payroll">{t('hr.tabs.payroll')}</Tabs.Tab>
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
        <Tabs.Panel value="attendance">
          <AttendancePanel />
        </Tabs.Panel>
        <Tabs.Panel value="leave">
          <LeavePanel />
        </Tabs.Panel>
        <Tabs.Panel value="timesheets">
          <TimesheetPanel />
        </Tabs.Panel>
        <Tabs.Panel value="payroll">
          <PayrollPanel />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
