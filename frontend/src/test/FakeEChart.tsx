import type { EChartOption } from '@/components/charts/echarts';

interface FakeEChartProps {
  option: EChartOption;
  ariaLabel?: string;
  height?: number | string;
}

/** Test double for the canvas chart: keeps the accessible label and exposes the option for assertions. */
export function EChart({ option, ariaLabel }: FakeEChartProps) {
  return <div role="img" aria-label={ariaLabel} data-testid="echart" data-option={JSON.stringify(option)} />;
}

/** The option a chart was rendered with (from its accessible name). */
export function chartOption(
  element: HTMLElement,
): { series: { name?: string; data: unknown[] }[] } & Record<string, unknown> {
  return JSON.parse(element.getAttribute('data-option') ?? '{}') as {
    series: { name?: string; data: unknown[] }[];
  };
}
