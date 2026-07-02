import { Group, Stack, Text } from '@mantine/core';

export type PipelineStage = {
  name: string;
  value: number;
  color: string;
};

/**
 * Horizontal stage-bar funnel: one row per stage with a readable label, a proportional bar, and the
 * right-aligned count. Replaces the bare `FunnelChart` (ERP §6.1/§6.4 #8) — that chart drew wedges with
 * numbers but no stage labels next to them, so the pipeline read as decoration rather than data. Bar
 * width is relative to the largest stage so the shape stays legible even when counts are small.
 */
export function PipelineBars({ stages }: { stages: PipelineStage[] }) {
  const max = Math.max(1, ...stages.map((s) => s.value));
  return (
    <Stack gap={10} role="list">
      {stages.map((s) => (
        <Group key={s.name} gap="sm" wrap="nowrap" role="listitem">
          <Text size="sm" c="dimmed" w={84} style={{ flexShrink: 0 }} truncate>
            {s.name}
          </Text>
          <div
            style={{
              flex: 1,
              height: 22,
              borderRadius: 6,
              background: 'var(--mantine-color-default-hover)',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                width: `${Math.max(s.value > 0 ? 4 : 0, (s.value / max) * 100)}%`,
                height: '100%',
                background: s.color,
                borderRadius: 6,
              }}
            />
          </div>
          <Text size="sm" fw={600} ff="monospace" ta="right" w={34} style={{ flexShrink: 0 }}>
            {s.value}
          </Text>
        </Group>
      ))}
    </Stack>
  );
}
