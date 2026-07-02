import { Card, Group, Progress, RingProgress, SimpleGrid, Stack, Text } from '@mantine/core';
import { IconChecklist, IconClipboardList, IconProgressCheck } from '@tabler/icons-react';
import { LiveBadge } from '../../components/LiveBadge';
import { KpiTile } from '../../components/charts/KpiTile';
import { GanttBoard } from '../../components/charts/GanttBoard';
import { DonutCard, type DonutDatum } from '../../components/charts/DonutCard';
import { categoryColors, moneyToNumber } from '../../components/charts/palette';
import { StatusBadge } from '../../components/StatusBadge';
import { useI18n } from '../../i18n';
import { useDowntime, useOee, useWorkOrders } from './api';

const OPEN_STATES = new Set(['DRAFT', 'RELEASED', 'IN_PROGRESS']);
const WIP_STATES = new Set(['RELEASED', 'IN_PROGRESS']);
const DISPATCH_STATES = new Set(['DRAFT', 'RELEASED']);

const oeeColor = (pct: number) => (pct >= 85 ? 'teal' : pct >= 70 ? 'orange' : 'red');

export function ManufacturingDashboardPanel() {
  const { t } = useI18n();
  const wo = useWorkOrders();
  const wos = wo.data ?? [];
  const oee = useOee();
  const downtime = useDowntime();

  const wip = wos.filter((w) => WIP_STATES.has(w.status ?? '')).length;
  const totalTo = wos.reduce((s, w) => s + moneyToNumber(w.qtyToProduce), 0);
  const totalDone = wos.reduce((s, w) => s + moneyToNumber(w.qtyProduced), 0);
  const achievement = totalTo > 0 ? `${((totalDone / totalTo) * 100).toFixed(1)}%` : '—';

  const active = wos.filter((w) => OPEN_STATES.has(w.status ?? ''));
  const dispatch = wos.filter((w) => DISPATCH_STATES.has(w.status ?? ''));
  const scheduled = wos
    .filter((w) => w.plannedStart)
    .slice(0, 8)
    .map((w) => {
      const to = moneyToNumber(w.qtyToProduce);
      const done = moneyToNumber(w.qtyProduced);
      return {
        id: w.id ?? 0,
        label: w.woNumber ?? '',
        start: w.plannedStart as string,
        end: (w.plannedEnd ?? w.plannedStart) as string,
        status: w.status ?? 'DRAFT',
        pct: to > 0 ? Math.round((done / to) * 100) : 0,
      };
    });

  const machines = oee.data ?? [];
  const downtimeData: DonutDatum[] = (downtime.data ?? []).map((d, i) => ({
    name: d.reason ?? '—',
    amount: String(d.minutes ?? 0),
    color: categoryColors[i % categoryColors.length] ?? categoryColors[0],
  }));
  const totalDowntime = (downtime.data ?? []).reduce((s, d) => s + (d.minutes ?? 0), 0);

  return (
    <Stack gap="md">
      <Group justify="flex-end">
        <LiveBadge />
      </Group>

      <SimpleGrid cols={{ base: 1, xs: 3 }}>
        <KpiTile label={t('manufacturing.dash.wip')} value={String(wip)} money={false} icon={<IconProgressCheck size={16} />} />
        <KpiTile label={t('manufacturing.dash.achievement')} value={achievement} money={false} icon={<IconChecklist size={16} />} />
        <KpiTile label={t('manufacturing.dash.output')} value={String(Math.round(totalDone))} money={false} icon={<IconClipboardList size={16} />} />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, lg: 2 }}>
        <Card withBorder padding="md">
          <Text fw={500} mb="sm">
            {t('manufacturing.dash.progressBoard')}
          </Text>
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
          <Text fw={500} mb="sm">
            {t('manufacturing.dash.dispatchQueue')}
          </Text>
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

      <Card withBorder padding="md">
        <Text fw={500} mb="sm">
          {t('manufacturing.dash.gantt')}
        </Text>
        {scheduled.length === 0 ? (
          <Text c="dimmed" size="sm" py="lg" ta="center">
            —
          </Text>
        ) : (
          <GanttBoard rows={scheduled} />
        )}
      </Card>

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <Card withBorder padding="md">
          <Text fw={500} mb="sm">
            {t('manufacturing.dash.oee')}
          </Text>
          {machines.length === 0 ? (
            <Text c="dimmed" size="sm" py="lg" ta="center">
              —
            </Text>
          ) : (
            <SimpleGrid cols={{ base: 1, xs: Math.min(3, machines.length) }}>
              {machines.map((m) => {
                const pct = Math.round(moneyToNumber(m.oee));
                return (
                  <Stack key={m.equipmentId} gap={2} align="center">
                    <RingProgress
                      size={104}
                      thickness={9}
                      roundCaps
                      sections={[{ value: pct, color: oeeColor(pct) }]}
                      label={<Text ta="center" fw={700} size="sm">{pct}%</Text>}
                    />
                    <Text size="xs" fw={500} ta="center" truncate>
                      {m.name}
                    </Text>
                    <Text size="xs" c="dimmed" ta="center">
                      A {Math.round(moneyToNumber(m.availability))} · P {Math.round(moneyToNumber(m.performance))} · Q {Math.round(moneyToNumber(m.quality))}
                    </Text>
                  </Stack>
                );
              })}
            </SimpleGrid>
          )}
        </Card>

        <DonutCard
          title={t('manufacturing.dash.downtime')}
          data={downtimeData}
          centerLabel={`${totalDowntime}m`}
        />
      </SimpleGrid>
    </Stack>
  );
}
