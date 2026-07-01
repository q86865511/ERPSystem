import { useState } from 'react';
import { Tabs } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import dayjs from 'dayjs';
import { PageHeader } from '../../components/PageHeader';
import { useI18n } from '../../i18n';
import { BalanceSheetPanel } from './BalanceSheetPanel';
import { IncomeStatementPanel } from './IncomeStatementPanel';
import { TrialBalancePanel } from './TrialBalancePanel';

export function ReportsPage() {
  const { t } = useI18n();
  const [asOf, setAsOf] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const value = asOf ?? undefined;

  return (
    <>
      <PageHeader
        title={t('reporting.title')}
        subtitle={t('reporting.subtitle')}
        onboardingId="module-reporting"
        actions={
          <DateInput
            label={t('field.asOf')}
            value={asOf}
            onChange={setAsOf}
            maw={180}
            popoverProps={{ withinPortal: true }}
          />
        }
      />
      <Tabs defaultValue="trial-balance" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="trial-balance">{t('reporting.tabs.trialBalance')}</Tabs.Tab>
          <Tabs.Tab value="income-statement">{t('reporting.tabs.incomeStatement')}</Tabs.Tab>
          <Tabs.Tab value="balance-sheet">{t('reporting.tabs.balanceSheet')}</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="trial-balance">
          <TrialBalancePanel asOf={value} />
        </Tabs.Panel>
        <Tabs.Panel value="income-statement">
          <IncomeStatementPanel asOf={value} />
        </Tabs.Panel>
        <Tabs.Panel value="balance-sheet">
          <BalanceSheetPanel asOf={value} />
        </Tabs.Panel>
      </Tabs>
    </>
  );
}
