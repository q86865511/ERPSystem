import { Badge, Card, Group, Progress, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconChecklist, IconClipboardList, IconProgressCheck } from '@tabler/icons-react';
import { KpiTile } from '../../components/charts/KpiTile';
import { moneyToNumber } from '../../components/charts/palette';
import { StatusBadge } from '../../components/StatusBadge';
import { useI18n } from '../../i18n';
import { useWorkOrders } from './api';

const OPEN_STATES = new Set(['DRAFT', 'RELEASED', 'IN_PROGRESS']);
const WIP_STATES = new Set(['RELEASED', 'IN_PROGRESS']);
const DISPATCH_STATES = new Set(['DRAFT', 'RELEASED']);

function PlannedCard({ title, note }: { title: string; note: string }) {
  const { t } = useI18n();
  return (
    <Card withBorder padding="md">
      <Group justify="space-between" mb="sm" wrap="nowrap">
        <Text fw={500}>{title}</Text>
        <Badge color="gray" variant="light" size="sm">
          {t('reporting.overview.planned')}
        </Badge>
      </Group>
      <Text c="dimmed" size="sm" py="lg" ta="center">
        {note}
      </Text>
    </Card>
  );
}

export function ManufacturingDashboardPanel() {
  const { t } = useI18n();
  const wo = useWorkOrders();
  const wos = wo.data ?? [];

  const live = (
    <Badge color="teal" variant="light" size="sm">
      {t('reporting.overview.liveData')}
    </Badge>
  );

  const wip = wos.filter((w) => WIP_STATES.has(w.status ?? '')).length;
  const totalTo = wos.reduce((s, w) => s + moneyToNumber(w.qtyToProduce), 0);
  const totalDone = wos.reduce((s, w) => s + moneyToNumber(w.qtyProduced), 0);
  const achievement = totalTo > 0 ? `${((totalDone / totalTo) * 100).toFixed(1)}%` : '—';

  const active = wos.filter((w) => OPEN_STATES.has(w.status ?? ''));
  const dispatch = wos.filter((w) => DISPATCH_STATES.has(w.status ?? ''));

  return (
    <Stack gap="md">
      <SimpleGrid cols={{ base: 1, xs: 3 }}>
        <KpiTile label={t('manufacturing.dash.wip')} value={String(wip)} money={false} icon={<IconProgressCheck size={16} />} status={live} />
        <KpiTile label={t('manufacturing.dash.achievement')} value={achievement} money={false} icon={<IconChecklist size={16} />} status={live} />
        <KpiTile label={t('manufacturing.dash.output')} value={String(Math.round(totalDone))} money={false} icon={<IconClipboardList size={16} />} status={live} />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, lg: 2 }}>
        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('manufacturing.dash.progressBoard')}</Text>
            {live}
          </Group>
          {active.length === 0 ? (
            <Text c="dimmed" size="sm" py="lg" ta="center">
              —
            </Text>
          ) : (
            <Stack gap="sm">
              {active.slice(0, 7).map((w) => {
                const to = moneyToNumber(w.qtyToProduce);
                const done = moneyToNumber(w.qtyProduced);
                const pct = to > 0 ? Math.min(100, Math.round((done / to) * 100)) : 0;
                return (
                  <div key={w.id}>
                    <Group justify="space-between" gap="xs" wrap="nowrap" mb={4}>
                      <Group gap={8} wrap="nowrap">
                        <Text size="sm" ff="monospace">
                          {w.woNumber}
                        </Text>
                        <StatusBadge status={w.status} />
                      </Group>
                      <Text size="xs" c="dimmed" ff="monospace">
                        {Math.round(done)} / {Math.round(to)} ({pct}%)
                      </Text>
                    </Group>
                    <Progress value={pct} size="sm" radius="xl" />
                  </div>
                );
              })}
            </Stack>
          )}
        </Card>

        <Card withBorder padding="md">
          <Group justify="space-between" mb="sm" wrap="nowrap">
            <Text fw={500}>{t('manufacturing.dash.dispatchQueue')}</Text>
            {live}
          </Group>
          {dispatch.length === 0 ? (
            <Text c="dimmed" size="sm" py="lg" ta="center">
              —
            </Text>
          ) : (
            <Stack gap={0}>
              {dispatch.slice(0, 8).map((w) => (
                <Group key={w.id} justify="space-between" py={9} wrap="nowrap" style={{ borderBottom: '0.5px solid var(--app-color-border)' }}>
                  <Text size="sm" ff="monospace">
                    {w.woNumber}
                  </Text>
                  <Group gap={10} wrap="nowrap">
                    <Text size="xs" c="dimmed" ff="monospace">
                      {Math.round(moneyToNumber(w.qtyToProduce))}
                    </Text>
                    <StatusBadge status={w.status} />
                  </Group>
                </Group>
              ))}
            </Stack>
          )}
        </Card>
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <PlannedCard title={t('manufacturing.dash.gantt')} note={t('reporting.overview.plannedNote')} />
        <PlannedCard title={t('manufacturing.dash.oee')} note={t('reporting.overview.plannedNote')} />
      </SimpleGrid>
    </Stack>
  );
}
