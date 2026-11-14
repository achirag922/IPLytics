import { EmptyState } from '@/components/states/EmptyState';
import { axisStyle, baseOption, SERIES_COLORS, useChartOption } from './chartTheme';
import { EChart } from './EChart';

interface PieChartProps {
  data: { name: string; value: number | null | undefined }[];
  ariaLabel: string;
  height?: number;
  donut?: boolean;
}

export function PieChart({ data, ariaLabel, height = 280, donut = true }: PieChartProps) {
  const option = useChartOption(
    (theme) => ({
      ...baseOption(theme, ariaLabel),
      tooltip: {
        ...(baseOption(theme, ariaLabel).tooltip as object),
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)',
      },
      legend: { bottom: 0, textStyle: { color: theme.text } },
      series: [
        {
          type: 'pie',
          radius: donut ? ['45%', '70%'] : '70%',
          center: ['50%', '45%'],
          data: data.map((d) => ({ name: d.name, value: d.value ?? 0 })),
          label: { color: theme.text, formatter: '{b}\n{c}' },
        },
      ],
    }),
    [data, ariaLabel, donut],
  );
  if (!data.some((d) => (d.value ?? 0) > 0)) return <EmptyState />;
  return <EChart option={option} height={height} ariaLabel={ariaLabel} />;
}

interface RadarChartProps {
  /** Axes with their maximum (scale only). */
  indicators: { name: string; max: number }[];
  series: { name: string; values: (number | null | undefined)[] }[];
  ariaLabel: string;
  height?: number;
}

export function RadarChart({ indicators, series, ariaLabel, height = 320 }: RadarChartProps) {
  const option = useChartOption(
    (theme) => ({
      ...baseOption(theme, ariaLabel),
      tooltip: { ...(baseOption(theme, ariaLabel).tooltip as object), trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: theme.text } },
      radar: {
        indicator: indicators,
        axisName: { color: theme.mutedText },
        splitLine: { lineStyle: { color: theme.splitLine } },
        axisLine: { lineStyle: { color: theme.axisLine } },
        splitArea: { show: false },
      },
      series: [
        {
          type: 'radar',
          data: series.map((s) => ({
            name: s.name,
            value: s.values.map((v) => v ?? 0),
            areaStyle: { opacity: 0.1 },
          })),
        },
      ],
    }),
    [indicators, series, ariaLabel],
  );
  if (indicators.length === 0 || series.length === 0) return <EmptyState />;
  return <EChart option={option} height={height} ariaLabel={ariaLabel} />;
}

interface HeatmapProps {
  xCategories: (string | number)[];
  yCategories: string[];
  /** [x index, y index, value] */
  values: [number, number, number | null][];
  ariaLabel: string;
  height?: number;
  valueName?: string;
}

export function Heatmap({
  xCategories,
  yCategories,
  values,
  ariaLabel,
  height = 200,
  valueName = 'Value',
}: HeatmapProps) {
  const option = useChartOption(
    (theme) => {
      const numbers = values.map((v) => v[2]).filter((v): v is number => v !== null);
      return {
        ...baseOption(theme, ariaLabel),
        aria: { enabled: true, label: { description: ariaLabel } },
        tooltip: {
          ...(baseOption(theme, ariaLabel).tooltip as object),
          formatter: (p: { value: [number, number, number | null] }) =>
            `${yCategories[p.value[1]]}, ${xCategories[p.value[0]]}: ${valueName} ${p.value[2] ?? '–'}`,
        },
        grid: { left: 8, right: 8, top: 8, bottom: 48 },
        xAxis: { type: 'category', data: xCategories, ...axisStyle(theme), splitArea: { show: true } },
        yAxis: { type: 'category', data: yCategories, ...axisStyle(theme), splitArea: { show: true } },
        visualMap: {
          min: numbers.length ? Math.min(...numbers) : 0,
          max: numbers.length ? Math.max(...numbers) : 1,
          orient: 'horizontal',
          left: 'center',
          bottom: 0,
          itemHeight: 120,
          calculable: false,
          text: ['High', 'Low'],
          textStyle: { color: theme.mutedText },
          inRange: { color: ['#dbeafe', SERIES_COLORS[0] ?? '#2563eb'] },
        },
        series: [{ type: 'heatmap', name: valueName, data: values, label: { show: true, color: '#0f172a' } }],
      };
    },
    [xCategories, yCategories, values, ariaLabel, valueName],
  );
  if (values.length === 0) return <EmptyState />;
  return <EChart option={option} height={height} ariaLabel={ariaLabel} />;
}
