import { useMemo } from 'react';
import { useTheme, type ResolvedTheme } from '@/lib/theme';
import type { EChartOption } from './echarts';

/** Categorical palette, distinguishable in both themes; decal patterns add a non-colour cue. */
export const SERIES_COLORS = [
  '#2563eb',
  '#f59e0b',
  '#059669',
  '#dc2626',
  '#7c3aed',
  '#0891b2',
  '#db2777',
  '#64748b',
];

export interface ChartTheme {
  text: string;
  mutedText: string;
  axisLine: string;
  splitLine: string;
  tooltipBackground: string;
  tooltipBorder: string;
  tooltipShadow: string;
  pointerShadow: string;
}

const THEMES: Record<ResolvedTheme, ChartTheme> = {
  light: {
    text: '#1e293b',
    mutedText: '#64748b',
    axisLine: '#cbd5e1',
    splitLine: '#e2e8f0',
    tooltipBackground: '#ffffff',
    tooltipBorder: '#e2e8f0',
    tooltipShadow: '0 10px 28px rgba(15,23,42,0.16)',
    pointerShadow: 'rgba(100,116,139,0.10)',
  },
  dark: {
    text: '#e2e8f0',
    mutedText: '#94a3b8',
    axisLine: '#475569',
    splitLine: '#1e293b',
    tooltipBackground: '#0f172a',
    tooltipBorder: '#334155',
    tooltipShadow: '0 10px 28px rgba(0,0,0,0.55)',
    pointerShadow: 'rgba(148,163,184,0.10)',
  },
};

export function useChartTheme(): ChartTheme {
  const { resolved } = useTheme();
  return THEMES[resolved];
}

/** Options every chart shares: palette, text colours, tooltip, accessible description and decal patterns. */
export function baseOption(theme: ChartTheme, ariaLabel: string): EChartOption {
  return {
    color: SERIES_COLORS,
    backgroundColor: 'transparent',
    textStyle: { color: theme.text, fontFamily: 'Inter, system-ui, sans-serif' },
    aria: { enabled: true, label: { description: ariaLabel }, decal: { show: true } },
    tooltip: {
      backgroundColor: theme.tooltipBackground,
      borderColor: theme.tooltipBorder,
      textStyle: { color: theme.text, fontSize: 12 },
      confine: true,
      padding: [8, 12],
      borderWidth: 1,
      extraCssText: `border-radius:10px;box-shadow:${theme.tooltipShadow};`,
      axisPointer: {
        lineStyle: { color: theme.axisLine, type: 'dashed' },
        shadowStyle: { color: theme.pointerShadow },
      },
    },
    toolbox: {
      right: 0,
      top: 0,
      itemSize: 13,
      feature: { saveAsImage: { title: 'Save as image', pixelRatio: 2 } },
      iconStyle: { borderColor: theme.mutedText },
    },
    animationDuration: 500,
    animationEasing: 'cubicOut',
  };
}

/** Legend shared by every chart: rounded markers, muted text, generous spacing. */
export function legendStyle(theme: ChartTheme) {
  return {
    icon: 'roundRect',
    itemWidth: 12,
    itemHeight: 8,
    itemGap: 16,
    textStyle: { color: theme.mutedText, fontSize: 12 },
    inactiveColor: theme.axisLine,
  };
}

export function axisStyle(theme: ChartTheme) {
  return {
    axisLine: { lineStyle: { color: theme.axisLine } },
    axisTick: { lineStyle: { color: theme.axisLine } },
    axisLabel: { color: theme.mutedText },
    splitLine: { lineStyle: { color: theme.splitLine } },
    nameTextStyle: { color: theme.mutedText },
  };
}

/**
 * Memoises an option object so the chart only redraws when its inputs change. Chart inputs are small (at most a
 * few hundred points), so their serialized form is a cheap, value-based key: a parent re-render with equal data
 * does not restart the chart animation.
 */
export function useChartOption(
  build: (theme: ChartTheme) => EChartOption,
  inputs: readonly unknown[],
): EChartOption {
  const theme = useChartTheme();
  const key = JSON.stringify(inputs);
  // `build` is recreated each render but depends only on `inputs`, which `key` captures by value.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  return useMemo(() => build(theme), [theme, key]);
}
