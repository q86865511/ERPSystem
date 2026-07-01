import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { BomsPanel } from './BomsPanel';
import { ManufacturingDashboardPanel } from './ManufacturingDashboardPanel';
import { ReorderReportPanel } from './ReorderReportPanel';
import { WorkOrdersPanel } from './WorkOrdersPanel';

export function ManufacturingPage() {
  const { t } = useI18n();
  return (
    <>
      <PageHeader
        title={t('manufacturing.page.title')}
        subtitle={t('manufacturing.page.subtitle')}
        onboardingId="module-manufacturing"
      />
      <Tabs defaultValue="dashboard" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="dashboard">{t('manufacturing.tabs.dashboard')}</Tabs.Tab>
          <Tabs.Tab value="work-orders">{t('manufacturing.tabs.workOrders')}</Tabs.Tab>
          <Tabs.Tab value="boms">{t('manufacturing.tabs.boms')}</Tabs.Tab>
          <Tabs.Tab value="reorder">{t('manufacturing.tabs.reorder')}</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="dashboard">
          <ManufacturingDashboardPanel />
        </Tabs.Panel>
        <Tabs.Panel value="work-orders">
          <WorkOrdersPanel />
        </Tabs.Panel>
        <Tabs.Panel value="boms">
          <BomsPanel />
        </Tabs.Panel>
        <Tabs.Panel value="reorder">
          <ReorderReportPanel />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
