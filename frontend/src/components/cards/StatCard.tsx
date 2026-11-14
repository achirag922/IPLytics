import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';

interface StatCardProps {
  label: string;
  value: ReactNode;
  /** Secondary line, e.g. the player or qualification behind the value. */
  hint?: ReactNode;
  icon?: LucideIcon;
  className?: string;
}

/** A single headline figure. Values arrive formatted; the card never computes them. */
export function StatCard({ label, value, hint, icon: Icon, className }: StatCardProps) {
  return (
    <Card className={cn('flex flex-col gap-1 p-4', className)}>
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
        {Icon && <Icon aria-hidden="true" className="size-4 text-muted-foreground" />}
      </div>
      <p className="text-2xl font-semibold tabular-nums tracking-tight">{value}</p>
      {hint && <div className="truncate text-sm text-muted-foreground">{hint}</div>}
    </Card>
  );
}

export function StatGrid({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('grid gap-3 sm:grid-cols-2 lg:grid-cols-4', className)}>{children}</div>;
}
