import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { PaymentsInPanel } from '../payments/PaymentsInPanel';
import { ArAgingPanel } from './ArAgingPanel';
import { CustomerReturnsPanel } from './CustomerReturnsPanel';
import { DeliveriesPanel } from './DeliveriesPanel';
import { SalesInvoicesPanel } from './SalesInvoicesPanel';
import { SalesOrdersPanel } from './SalesOrdersPanel';

export function SalesPage() {
  const { t } = useI18n();
  return (
    <>
      <PageHeader title={t('sales.title')} subtitle={t('sales.subtitle')} onboardingId="module-sales" />
      <Tabs defaultValue="orders" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="orders">{t('sales.tabs.orders')}</Tabs.Tab>
          <Tabs.Tab value="deliveries">{t('sales.tabs.deliveries')}</Tabs.Tab>
          <Tabs.Tab value="invoices">{t('sales.tabs.invoices')}</Tabs.Tab>
          <Tabs.Tab value="receipts">{t('sales.tabs.receipts')}</Tabs.Tab>
          <Tabs.Tab value="returns">{t('sales.tabs.returns')}</Tabs.Tab>
          <Tabs.Tab value="ar-aging">{t('sales.tabs.arAging')}</Tabs.Tab>
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
