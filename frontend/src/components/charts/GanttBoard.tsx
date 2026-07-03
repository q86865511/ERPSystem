import { Group, Stack, Text, Tooltip } from '@mantine/core';
import { StatusBadge } from '../StatusBadge';

export type GanttRow = {
  id: number | string;
  label: string;
  start: string;
  end: string;
  status: string;
  pct: number;
};

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'var(--mantine-color-gray-5)',
  RELEASED: 'var(--mantine-color-brand-4)',
  IN_PROGRESS: 'var(--erp-chart-1)',
  DONE: 'var(--erp-positive-text)',
  CANCELLED: 'var(--erp-negative-text)',
};

/** A minimal self-drawn Gantt: one row per work order, bar positioned by its planned start→end window,
 *  coloured by status with a translucent progress fill. */
export function GanttBoard({ rows }: { rows: GanttRow[] }) {
  if (rows.length === 0) {
    return null;
  }
  const min = Math.min(...rows.map((r) => new Date(r.start).getTime()));
  const max = Math.max(...rows.map((r) => new Date(r.end).getTime()));
  const span = Math.max(1, max - min);
  const pos = (t: string) => ((new Date(t).getTime() - min) / span) * 100;

  return (
    <Stack gap={8}>
      {rows.map((r) => {
        const left = pos(r.start);
        const width = Math.max(2, pos(r.end) - left);
        return (
          <Group key={r.id} gap="xs" wrap="nowrap">
            <Text size="xs" ff="monospace" w={92} style={{ flexShrink: 0 }} truncate>
              {r.label}
            </Text>
            <div
              style={{
                position: 'relative',
                flex: 1,
                height: 18,
                background: 'var(--mantine-color-default-hover)',
                borderRadius: 4,
              }}
            >
              <Tooltip label={`${r.start} → ${r.end}`} withArrow>
                <div
                  style={{
                    position: 'absolute',
                    left: `${left}%`,
                    width: `${width}%`,
                    top: 0,
                    height: 18,
                    background: STATUS_COLOR[r.status] ?? STATUS_COLOR.DRAFT,
                    borderRadius: 4,
                    overflow: 'hidden',
                  }}
                >
                  <div style={{ width: `${Math.min(100, r.pct)}%`, height: '100%', background: 'rgba(255,255,255,0.4)' }} />
                </div>
              </Tooltip>
            </div>
            <StatusBadge status={r.status} />
          </Group>
        );
      })}
    </Stack>
  );
}
