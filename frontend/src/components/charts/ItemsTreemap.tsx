import { ResponsiveContainer, Treemap } from 'recharts';
import { treemapFill, treemapLabelColor } from './palette';

export type ItemsTreemapDatum = { name: string; value: number; status: string };

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
  const key = (status as keyof typeof treemapFill) in treemapFill ? (status as keyof typeof treemapFill) : 'OK';
  return (
    <g>
      <rect
        x={x}
        y={y}
        width={width}
        height={height}
        style={{ fill: treemapFill[key], stroke: 'var(--mantine-color-body)', strokeWidth: 2 }}
      />
      {width > 46 && height > 18 && (
        <text x={x + 5} y={y + 15} fill={treemapLabelColor[key]} fontSize={11} style={{ pointerEvents: 'none' }}>
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
