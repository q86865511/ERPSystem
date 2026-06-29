import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { FiscalPeriodsPanel } from './FiscalPeriodsPanel';
import { ManualEntryPanel } from './ManualEntryPanel';

export function LedgerPage() {
  const { t } = useI18n();
  return (
    <>
      <PageHeader title={t('ledger.page.title')} subtitle={t('ledger.page.subtitle')} />
      <Tabs defaultValue="manual-entry" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="manual-entry">{t('ledger.tabs.manualEntry')}</Tabs.Tab>
          <Tabs.Tab value="periods">{t('ledger.tabs.periods')}</Tabs.Tab>
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
