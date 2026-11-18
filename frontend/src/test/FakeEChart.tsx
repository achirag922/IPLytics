import type { ChartItemEvent } from '@/components/charts/EChart';
import type { EChartOption } from '@/components/charts/echarts';

interface FakeEChartProps {
  option: EChartOption;
  ariaLabel?: string;
  height?: number | string;
  onItemClick?: (event: ChartItemEvent) => void;
}

type AxisOption = { type?: string; data?: unknown[] } | undefined;

/** Category labels of a chart option (category axis, else the first series' item names). */
function categoriesOf(option: EChartOption): string[] {
  const o = option as { xAxis?: AxisOption; yAxis?: AxisOption; series?: { data?: unknown[] }[] };
  const axis = [o.xAxis, o.yAxis].find((a) => a?.type === 'category');
  if (axis?.data) return axis.data.map(String);
  return (o.series?.[0]?.data ?? []).map((d) =>
    typeof d === 'object' && d !== null && 'name' in d ? String((d as { name: unknown }).name) : String(d),
  );
}

/**
 * Test double for the canvas chart: keeps the accessible name, exposes the option for assertions and, when the
 * chart is clickable, renders one button per item ("<chart name>: <category>") to simulate a click.
 */
export function EChart({ option, ariaLabel, onItemClick }: FakeEChartProps) {
  return (
    <div role="img" aria-label={ariaLabel} data-testid="echart" data-option={JSON.stringify(option)}>
      {onItemClick &&
        categoriesOf(option).map((name, dataIndex) => (
          <button
            key={`${name}-${dataIndex}`}
            type="button"
            aria-label={`${ariaLabel}: ${name}`}
            onClick={() => onItemClick({ dataIndex, seriesIndex: 0, name })}
          />
        ))}
    </div>
  );
}

/** The option a chart was rendered with. */
export function chartOption(
  element: HTMLElement,
): { series: { name?: string; data: unknown[] }[] } & Record<string, unknown> {
  return JSON.parse(element.getAttribute('data-option') ?? '{}') as {
    series: { name?: string; data: unknown[] }[];
  };
}
