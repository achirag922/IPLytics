import { useEffect, useRef } from 'react';
import { echarts, type EChartOption } from './echarts';

type EChartInstance = ReturnType<typeof echarts.init>;

/** A clicked data item: its position and category, for drill-down and cross-filtering. */
export interface ChartItemEvent {
  dataIndex: number;
  seriesIndex: number;
  /** Category (x or y axis label) or item name. */
  name: string;
}

interface EChartProps {
  option: EChartOption;
  height?: number | string;
  className?: string;
  /** Accessible description of what the chart shows. */
  ariaLabel?: string;
  /** Called when a data item (bar, point, slice) is clicked. */
  onItemClick?: (event: ChartItemEvent) => void;
}

/** Thin React wrapper around ECharts: owns the instance lifecycle, resizing and click events. */
export function EChart({ option, height = 320, className, ariaLabel, onItemClick }: EChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<EChartInstance | null>(null);
  const clickRef = useRef(onItemClick);

  useEffect(() => {
    clickRef.current = onItemClick;
  }, [onItemClick]);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return;
    const chart = echarts.init(element);
    chartRef.current = chart;
    chart.on('click', (params) => {
      const p = params as { dataIndex?: number; seriesIndex?: number; name?: string };
      clickRef.current?.({
        dataIndex: p.dataIndex ?? 0,
        seriesIndex: p.seriesIndex ?? 0,
        name: String(p.name ?? ''),
      });
    });
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
      style={{ height, width: '100%', cursor: onItemClick ? 'pointer' : undefined }}
    />
  );
}
