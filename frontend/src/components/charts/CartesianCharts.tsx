import { EmptyState } from '@/components/states/EmptyState';
import { axisStyle, baseOption, legendStyle, useChartOption } from './chartTheme';
import { EChart } from './EChart';

export interface SeriesInput {
  name: string;
  data: (number | null | undefined)[];
  /** Series with the same stack id are stacked (e.g. wins and losses). */
  stack?: string;
  color?: string;
}

export interface CartesianChartProps {
  categories: (string | number)[];
  series: SeriesInput[];
  ariaLabel: string;
  height?: number;
  xAxisName?: string;
  yAxisName?: string;
  /** Horizontal bars (categories on the y axis), for ranked lists. */
  horizontal?: boolean;
  emptyTitle?: string;
  /** Drill-down / cross-filter: called with the clicked category's index and label. */
  onItemClick?: (index: number, category: string | number) => void;
  /** Adds zoom and pan along the category axis (long series such as seasons or overs). */
  zoom?: boolean;
}

type Kind = 'bar' | 'line' | 'area';

function hasData(categories: unknown[], series: SeriesInput[]): boolean {
  return categories.length > 0 && series.some((s) => s.data.some((v) => v !== null && v !== undefined));
}

function CartesianChart({
  kind,
  categories,
  series,
  ariaLabel,
  height = 300,
  xAxisName,
  yAxisName,
  horizontal = false,
  emptyTitle = 'No data available',
  onItemClick,
  zoom = false,
}: CartesianChartProps & { kind: Kind }) {
  const option = useChartOption(
    (theme) => {
      const categoryAxis = {
        type: 'category' as const,
        data: categories,
        name: horizontal ? yAxisName : xAxisName,
        nameLocation: 'middle' as const,
        nameGap: 28,
        inverse: horizontal,
        ...axisStyle(theme),
        splitLine: { show: false },
      };
      const valueAxis = {
        type: 'value' as const,
        name: horizontal ? xAxisName : yAxisName,
        nameLocation: 'middle' as const,
        nameGap: 40,
        ...axisStyle(theme),
      };
      return {
        ...baseOption(theme, ariaLabel),
        tooltip: { ...(baseOption(theme, ariaLabel).tooltip as object), trigger: 'axis' },
        legend: series.length > 1 ? { top: 0, left: 0, ...legendStyle(theme) } : undefined,
        grid: {
          left: 8,
          right: 16,
          top: 36,
          bottom: (xAxisName ? 32 : 8) + (zoom ? 36 : 0),
        },
        dataZoom: zoom
          ? [
              { type: 'inside', [horizontal ? 'yAxisIndex' : 'xAxisIndex']: 0 },
              { type: 'slider', [horizontal ? 'yAxisIndex' : 'xAxisIndex']: 0, height: 18, bottom: 4 },
            ]
          : undefined,
        xAxis: horizontal ? valueAxis : categoryAxis,
        yAxis: horizontal ? categoryAxis : valueAxis,
        series: series.map((s) => ({
          name: s.name,
          type: kind === 'bar' ? 'bar' : 'line',
          data: s.data.map((v) => (v === undefined ? null : v)),
          stack: s.stack,
          smooth: kind !== 'bar',
          symbolSize: 6,
          connectNulls: false,
          symbol: 'circle',
          showSymbol: categories.length <= 24,
          lineStyle: kind === 'bar' ? undefined : { width: 2.5 },
          areaStyle: kind === 'area' ? { opacity: 0.18 } : undefined,
          itemStyle: {
            ...(s.color ? { color: s.color } : {}),
            ...(kind === 'bar' && !s.stack ? { borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0] } : {}),
          },
          barMaxWidth: 28,
          emphasis: { focus: 'series' },
        })),
      };
    },
    [kind, categories, series, ariaLabel, xAxisName, yAxisName, horizontal, zoom],
  );
  if (!hasData(categories, series)) return <EmptyState title={emptyTitle} />;
  return (
    <EChart
      option={option}
      height={height}
      ariaLabel={ariaLabel}
      onItemClick={
        onItemClick
          ? (event) => {
              const category = categories[event.dataIndex];
              if (category !== undefined) onItemClick(event.dataIndex, category);
            }
          : undefined
      }
    />
  );
}

export function BarChart(props: CartesianChartProps) {
  return <CartesianChart kind="bar" {...props} />;
}

export function LineChart(props: CartesianChartProps) {
  return <CartesianChart kind="line" {...props} />;
}

export function AreaChart(props: CartesianChartProps) {
  return <CartesianChart kind="area" {...props} />;
}
