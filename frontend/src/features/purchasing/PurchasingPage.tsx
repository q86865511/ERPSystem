import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { PaymentsOutPanel } from '../payments/PaymentsOutPanel';
import { ApAgingPanel } from './ApAgingPanel';
import { GoodsReceiptsPanel } from './GoodsReceiptsPanel';
import { PurchaseOrdersPanel } from './PurchaseOrdersPanel';
import { VendorBillsPanel } from './VendorBillsPanel';

export function PurchasingPage() {
  return (
    <>
      <PageHeader title="Purchasing" subtitle="Procure to pay: order → receive → bill → pay" />
      <Tabs defaultValue="orders" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="orders">Purchase orders</Tabs.Tab>
          <Tabs.Tab value="receipts">Goods receipts</Tabs.Tab>
          <Tabs.Tab value="bills">Vendor bills</Tabs.Tab>
          <Tabs.Tab value="payments">Payments</Tabs.Tab>
          <Tabs.Tab value="ap-aging">AP aging</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="orders">
          <PurchaseOrdersPanel />
        </Tabs.Panel>
        <Tabs.Panel value="receipts">
          <GoodsReceiptsPanel />
        </Tabs.Panel>
        <Tabs.Panel value="bills">
          <VendorBillsPanel />
        </Tabs.Panel>
        <Tabs.Panel value="payments">
          <PaymentsOutPanel />
        </Tabs.Panel>
        <Tabs.Panel value="ap-aging">
          <ApAgingPanel />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
