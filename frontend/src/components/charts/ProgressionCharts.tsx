import { EmptyState } from '@/components/states/EmptyState';
import { axisStyle, baseOption, SERIES_COLORS, useChartOption } from './chartTheme';
import { EChart } from './EChart';

/** One innings of the V1.4 progression API, already aggregated per over by the backend. */
export interface InningsSeries {
  name: string;
  overs: { over: number; runs: number; wickets: number; cumulativeRuns: number; cumulativeWickets: number }[];
}

interface ProgressionChartProps {
  innings: InningsSeries[];
  ariaLabel: string;
  height?: number;
}

const hasOvers = (innings: InningsSeries[]) => innings.some((i) => i.overs.length > 0);

/**
 * Worm chart: running total after each over, one line per innings, starting from 0/0. Overs in which wickets fell
 * are marked with a labelled point (count shown), so wickets do not rely on colour.
 */
export function WormChart({ innings, ariaLabel, height = 340 }: ProgressionChartProps) {
  const option = useChartOption(
    (theme) => ({
      ...baseOption(theme, ariaLabel),
      tooltip: { ...(baseOption(theme, ariaLabel).tooltip as object), trigger: 'axis' },
      legend: { top: 0, textStyle: { color: theme.text } },
      grid: { left: 8, right: 16, top: 36, bottom: 32 },
      xAxis: {
        type: 'value',
        name: 'Over',
        nameLocation: 'middle',
        nameGap: 26,
        min: 0,
        minInterval: 1,
        ...axisStyle(theme),
      },
      yAxis: { type: 'value', name: 'Runs', nameLocation: 'middle', nameGap: 40, ...axisStyle(theme) },
      series: innings.flatMap((inn, index) => {
        const color = SERIES_COLORS[index % SERIES_COLORS.length];
        return [
          {
            name: inn.name,
            type: 'line',
            showSymbol: false,
            itemStyle: { color },
            lineStyle: { width: 2.5, type: index % 2 === 0 ? 'solid' : 'dashed' },
            data: [[0, 0], ...inn.overs.map((o) => [o.over, o.cumulativeRuns])],
          },
          {
            name: `${inn.name} wickets`,
            type: 'scatter',
            symbol: 'circle',
            symbolSize: 11,
            itemStyle: { color, borderColor: theme.tooltipBackground, borderWidth: 2 },
            label: {
              show: true,
              position: 'top',
              color: theme.text,
              formatter: (p: { data: number[] }) => `${p.data[2]}W`,
            },
            tooltip: { valueFormatter: undefined },
            data: inn.overs.filter((o) => o.wickets > 0).map((o) => [o.over, o.cumulativeRuns, o.wickets]),
          },
        ];
      }),
    }),
    [innings, ariaLabel],
  );
  if (!hasOvers(innings)) return <EmptyState title="No ball-by-ball data for this match" />;
  return <EChart option={option} height={height} ariaLabel={ariaLabel} />;
}

/**
 * Manhattan chart: runs scored in each over, innings side by side. Wicket overs carry a "W" marker with the count.
 */
export function ManhattanChart({ innings, ariaLabel, height = 340 }: ProgressionChartProps) {
  const option = useChartOption(
    (theme) => {
      const maxOver = Math.max(0, ...innings.flatMap((i) => i.overs.map((o) => o.over)));
      const categories = Array.from({ length: maxOver }, (_, i) => i + 1);
      return {
        ...baseOption(theme, ariaLabel),
        tooltip: { ...(baseOption(theme, ariaLabel).tooltip as object), trigger: 'axis' },
        legend: { top: 0, textStyle: { color: theme.text } },
        grid: { left: 8, right: 16, top: 36, bottom: 32 },
        xAxis: {
          type: 'category',
          data: categories,
          name: 'Over',
          nameLocation: 'middle',
          nameGap: 26,
          ...axisStyle(theme),
        },
        yAxis: { type: 'value', name: 'Runs', nameLocation: 'middle', nameGap: 36, ...axisStyle(theme) },
        series: innings.map((inn, index) => {
          const byOver = new Map(inn.overs.map((o) => [o.over, o]));
          return {
            name: inn.name,
            type: 'bar',
            barMaxWidth: 16,
            itemStyle: { color: SERIES_COLORS[index % SERIES_COLORS.length] },
            data: categories.map((over) => {
              const o = byOver.get(over);
              return o
                ? {
                    value: o.runs,
                    label:
                      o.wickets > 0
                        ? {
                            show: true,
                            position: 'top',
                            formatter: `${o.wickets}W`,
                            color: theme.text,
                            fontSize: 10,
                          }
                        : undefined,
                  }
                : null;
            }),
          };
        }),
      };
    },
    [innings, ariaLabel],
  );
  if (!hasOvers(innings)) return <EmptyState title="No ball-by-ball data for this match" />;
  return <EChart option={option} height={height} ariaLabel={ariaLabel} />;
}
