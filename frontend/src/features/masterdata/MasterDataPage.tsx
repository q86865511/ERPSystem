import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { ItemsPanel } from './ItemsPanel';
import { LocationsPanel } from './LocationsPanel';
import { PartnersPanel } from './PartnersPanel';
import { WarehousesPanel } from './WarehousesPanel';

export function MasterDataPage() {
  return (
    <>
      <PageHeader title="Master Data" subtitle="Items, partners, warehouses and locations" />
      <Tabs defaultValue="items" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="items">Items</Tabs.Tab>
          <Tabs.Tab value="partners">Partners</Tabs.Tab>
          <Tabs.Tab value="warehouses">Warehouses</Tabs.Tab>
          <Tabs.Tab value="locations">Locations</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="items">
          <ItemsPanel />
        </Tabs.Panel>
        <Tabs.Panel value="partners">
          <PartnersPanel />
        </Tabs.Panel>
        <Tabs.Panel value="warehouses">
          <WarehousesPanel />
        </Tabs.Panel>
        <Tabs.Panel value="locations">
          <LocationsPanel />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
