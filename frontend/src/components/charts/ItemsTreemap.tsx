import { ResponsiveContainer, Treemap } from 'recharts';

export type ItemsTreemapDatum = { name: string; value: number; status: string };

const STATUS_FILL: Record<string, string> = {
  OUT: 'var(--mantine-color-red-6)',
  LOW: 'var(--mantine-color-orange-5)',
  OK: 'var(--mantine-color-teal-6)',
};

type CellProps = {
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  name?: string;
  status?: string;
};

/** A single treemap tile, filled by its item's OUT/LOW/OK status and labelled with the SKU when it fits. */
function StatusCell({ x = 0, y = 0, width = 0, height = 0, name = '', status = 'OK' }: CellProps) {
  return (
    <g>
      <rect
        x={x}
        y={y}
        width={width}
        height={height}
        style={{ fill: STATUS_FILL[status] ?? STATUS_FILL.OK, stroke: 'var(--mantine-color-body)', strokeWidth: 2 }}
      />
      {width > 46 && height > 18 && (
        <text x={x + 5} y={y + 15} fill="#fff" fontSize={11} style={{ pointerEvents: 'none' }}>
          {name}
        </text>
      )}
    </g>
  );
}

/** Inventory heat treemap: tiles sized by on-hand value, coloured by stock status. */
export function ItemsTreemap({ data, height = 220 }: { data: ItemsTreemapDatum[]; height?: number }) {
  if (data.length === 0) {
    return null;
  }
  return (
    <ResponsiveContainer width="100%" height={height}>
      <Treemap data={data} dataKey="value" nameKey="name" isAnimationActive={false} content={<StatusCell />} />
    </ResponsiveContainer>
  );
}
