import { Tabs } from '@mantine/core';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { ItemsPanel } from './ItemsPanel';
import { LocationsPanel } from './LocationsPanel';
import { PartnersPanel } from './PartnersPanel';
import { WarehousesPanel } from './WarehousesPanel';

export function MasterDataPage() {
  const { t } = useI18n();
  return (
    <>
      <PageHeader title={t('nav.masterData')} subtitle={t('masterdata.subtitle')} onboardingId="module-masterdata" />
      <Tabs defaultValue="items" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="items">{t('masterdata.tabs.items')}</Tabs.Tab>
          <Tabs.Tab value="partners">{t('masterdata.tabs.partners')}</Tabs.Tab>
          <Tabs.Tab value="warehouses">{t('masterdata.tabs.warehouses')}</Tabs.Tab>
          <Tabs.Tab value="locations">{t('masterdata.tabs.locations')}</Tabs.Tab>
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
