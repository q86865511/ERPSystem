import { useState } from 'react';
import { Tabs } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import dayjs from 'dayjs';
import { PageHeader } from '../../components/PageHeader';
import { BalanceSheetPanel } from './BalanceSheetPanel';
import { IncomeStatementPanel } from './IncomeStatementPanel';
import { TrialBalancePanel } from './TrialBalancePanel';

export function ReportsPage() {
  const [asOf, setAsOf] = useState<string | null>(dayjs().format('YYYY-MM-DD'));
  const value = asOf ?? undefined;

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Financial statements as of a date"
        actions={
          <DateInput
            label="As of"
            value={asOf}
            onChange={setAsOf}
            maw={180}
            popoverProps={{ withinPortal: true }}
          />
        }
      />
      <Tabs defaultValue="trial-balance" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="trial-balance">Trial balance</Tabs.Tab>
          <Tabs.Tab value="income-statement">Income statement</Tabs.Tab>
          <Tabs.Tab value="balance-sheet">Balance sheet</Tabs.Tab>
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
