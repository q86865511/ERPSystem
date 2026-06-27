import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { PaymentsInPanel } from '../payments/PaymentsInPanel';
import { ArAgingPanel } from './ArAgingPanel';
import { CustomerReturnsPanel } from './CustomerReturnsPanel';
import { DeliveriesPanel } from './DeliveriesPanel';
import { SalesInvoicesPanel } from './SalesInvoicesPanel';
import { SalesOrdersPanel } from './SalesOrdersPanel';

export function SalesPage() {
  return (
    <>
      <PageHeader title="Sales" subtitle="Order to cash: order → deliver → invoice → receipt" />
      <Tabs defaultValue="orders" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="orders">Sales orders</Tabs.Tab>
          <Tabs.Tab value="deliveries">Deliveries</Tabs.Tab>
          <Tabs.Tab value="invoices">Invoices</Tabs.Tab>
          <Tabs.Tab value="receipts">Receipts</Tabs.Tab>
          <Tabs.Tab value="returns">Returns</Tabs.Tab>
          <Tabs.Tab value="ar-aging">AR aging</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="orders">
          <SalesOrdersPanel />
        </Tabs.Panel>
        <Tabs.Panel value="deliveries">
          <DeliveriesPanel />
        </Tabs.Panel>
        <Tabs.Panel value="invoices">
          <SalesInvoicesPanel />
        </Tabs.Panel>
        <Tabs.Panel value="receipts">
          <PaymentsInPanel />
        </Tabs.Panel>
        <Tabs.Panel value="returns">
          <CustomerReturnsPanel />
        </Tabs.Panel>
        <Tabs.Panel value="ar-aging">
          <ArAgingPanel />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
