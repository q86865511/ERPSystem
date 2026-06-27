import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { BomsPanel } from './BomsPanel';
import { ReorderReportPanel } from './ReorderReportPanel';
import { WorkOrdersPanel } from './WorkOrdersPanel';

export function ManufacturingPage() {
  return (
    <>
      <PageHeader title="Manufacturing" subtitle="Make: BOM → work order → issue → complete" />
      <Tabs defaultValue="work-orders" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="work-orders">Work orders</Tabs.Tab>
          <Tabs.Tab value="boms">Bills of material</Tabs.Tab>
          <Tabs.Tab value="reorder">Reorder report</Tabs.Tab>
        </Tabs.List>
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
