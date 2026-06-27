import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { FiscalPeriodsPanel } from './FiscalPeriodsPanel';
import { ManualEntryPanel } from './ManualEntryPanel';

export function LedgerPage() {
  return (
    <>
      <PageHeader title="Ledger" subtitle="Manual journal entries and fiscal-period close" />
      <Tabs defaultValue="manual-entry" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="manual-entry">Manual entry</Tabs.Tab>
          <Tabs.Tab value="periods">Fiscal periods</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="manual-entry">
          <ManualEntryPanel />
        </Tabs.Panel>
        <Tabs.Panel value="periods">
          <FiscalPeriodsPanel />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
