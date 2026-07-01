import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { PaymentsOutPanel } from '../payments/PaymentsOutPanel';
import { ApAgingPanel } from './ApAgingPanel';
import { GoodsReceiptsPanel } from './GoodsReceiptsPanel';
import { PurchaseOrdersPanel } from './PurchaseOrdersPanel';
import { VendorBillsPanel } from './VendorBillsPanel';

export function PurchasingPage() {
  const { t } = useI18n();
  return (
    <>
      <PageHeader title={t('purchasing.title')} subtitle={t('purchasing.subtitle')} onboardingId="module-purchasing" />
      <Tabs defaultValue="orders" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="orders">{t('purchasing.tabs.orders')}</Tabs.Tab>
          <Tabs.Tab value="receipts">{t('purchasing.tabs.receipts')}</Tabs.Tab>
          <Tabs.Tab value="bills">{t('purchasing.tabs.bills')}</Tabs.Tab>
          <Tabs.Tab value="payments">{t('purchasing.tabs.payments')}</Tabs.Tab>
          <Tabs.Tab value="ap-aging">{t('purchasing.tabs.apAging')}</Tabs.Tab>
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
