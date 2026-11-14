import { useEffect, useRef } from 'react';
import { echarts, type EChartOption } from './echarts';

type EChartInstance = ReturnType<typeof echarts.init>;

interface EChartProps {
  option: EChartOption;
  height?: number | string;
  className?: string;
  /** Accessible description of what the chart shows. */
  ariaLabel?: string;
}

/** Thin React wrapper around ECharts: owns the instance lifecycle and resizes with its container. */
export function EChart({ option, height = 320, className, ariaLabel }: EChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<EChartInstance | null>(null);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return;
    const chart = echarts.init(element);
    chartRef.current = chart;
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(element);
    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    chartRef.current?.setOption(option, { notMerge: true });
  }, [option]);

  return (
    <div
      ref={containerRef}
      role="img"
      aria-label={ariaLabel}
      className={className}
      style={{ height, width: '100%' }}
    />
  );
}
