import { EmptyState } from '@/components/states/EmptyState';
import { axisStyle, baseOption, useChartOption } from './chartTheme';
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
        legend: series.length > 1 ? { top: 0, textStyle: { color: theme.text } } : undefined,
        grid: {
          left: 8,
          right: 16,
          top: series.length > 1 ? 36 : 16,
          bottom: xAxisName ? 32 : 8,
        },
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
          areaStyle: kind === 'area' ? { opacity: 0.15 } : undefined,
          itemStyle: s.color ? { color: s.color } : undefined,
          barMaxWidth: 28,
          emphasis: { focus: 'series' },
        })),
      };
    },
    [kind, categories, series, ariaLabel, xAxisName, yAxisName, horizontal],
  );
  if (!hasData(categories, series)) return <EmptyState title={emptyTitle} />;
  return <EChart option={option} height={height} ariaLabel={ariaLabel} />;
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
