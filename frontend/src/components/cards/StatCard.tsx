import { ArrowDownRight, ArrowRight, ArrowUpRight, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';

export interface Trend {
  /** Direction of the change the backend reported (its sign), never inferred from colour alone. */
  direction: 'up' | 'down' | 'flat';
  /** Text such as "+4.20 vs 2025". */
  label: string;
}

interface StatCardProps {
  label: string;
  value: ReactNode;
  /** Secondary line, e.g. the player or qualification behind the value. */
  hint?: ReactNode;
  icon?: LucideIcon;
  trend?: Trend;
  /** Small inline trend line (e.g. the value over seasons); decorative, the value is in the text. */
  sparkline?: (number | null | undefined)[];
  className?: string;
}

const TREND_ICON = { up: ArrowUpRight, down: ArrowDownRight, flat: ArrowRight } as const;

/** A single headline figure. Values arrive formatted; the card never computes them. */
export function StatCard({ label, value, hint, icon: Icon, trend, sparkline, className }: StatCardProps) {
  const TrendIcon = trend ? TREND_ICON[trend.direction] : undefined;
  return (
    <Card className={cn('card-interactive relative flex flex-col gap-1 overflow-hidden p-4', className)}>
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
        {Icon && (
          <span
            aria-hidden="true"
            className="grid size-8 place-items-center rounded-lg bg-primary/10 text-primary"
          >
            <Icon className="size-4" />
          </span>
        )}
      </div>
      <div className="flex items-end justify-between gap-3">
        <p className="text-2xl font-bold tabular-nums tracking-tight sm:text-[1.7rem]">{value}</p>
        {sparkline && <Sparkline values={sparkline} />}
      </div>
      {trend && TrendIcon && (
        <p
          className={cn(
            'flex w-fit items-center gap-1 rounded-full px-1.5 py-0.5 text-xs font-medium',
            trend.direction === 'up' && 'bg-primary/10 text-primary',
            trend.direction === 'down' && 'bg-orange-500/10 text-orange-700 dark:text-orange-400',
            trend.direction === 'flat' && 'bg-muted text-muted-foreground',
          )}
        >
          <TrendIcon aria-hidden="true" className="size-3.5" />
          <span>{trend.label}</span>
        </p>
      )}
      {hint && <div className="truncate text-sm text-muted-foreground">{hint}</div>}
    </Card>
  );
}

export function StatGrid({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('grid gap-3 sm:grid-cols-2 lg:grid-cols-4', className)}>{children}</div>;
}

/** A tiny SVG line; scaling the points to the box is presentation only. */
export function Sparkline({
  values,
  width = 88,
  height = 28,
}: {
  values: (number | null | undefined)[];
  width?: number;
  height?: number;
}) {
  const numbers = values.filter((v): v is number => typeof v === 'number');
  if (numbers.length < 2) return null;
  const min = Math.min(...numbers);
  const span = Math.max(...numbers) - min || 1;
  const step = width / (values.length - 1);
  const points = values
    .map((v, i) =>
      typeof v === 'number'
        ? `${(i * step).toFixed(1)},${(height - 2 - ((v - min) / span) * (height - 4)).toFixed(1)}`
        : null,
    )
    .filter((p): p is string => p !== null)
    .join(' ');
  return (
    <svg
      aria-hidden="true"
      width={width}
      height={height}
      className="shrink-0 text-primary"
      data-testid="sparkline"
    >
      <polyline points={points} fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinejoin="round" />
    </svg>
  );
}
