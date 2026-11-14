import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

interface LoadingProps {
  /** Announced to screen readers while the content loads. */
  label?: string;
  className?: string;
}

function LoadingRegion({
  label = 'Loading',
  className,
  children,
}: LoadingProps & { children: React.ReactNode }) {
  return (
    <div role="status" aria-live="polite" aria-busy="true" className={className}>
      <span className="sr-only">{label}…</span>
      {children}
    </div>
  );
}

export function StatCardSkeleton({
  count = 4,
  label = 'Loading statistics',
  className,
}: LoadingProps & { count?: number }) {
  return (
    <LoadingRegion label={label} className={cn('grid gap-3 sm:grid-cols-2 lg:grid-cols-4', className)}>
      {Array.from({ length: count }, (_, i) => (
        <Card key={i} className="p-4">
          <Skeleton className="mb-3 h-3 w-24" />
          <Skeleton className="h-7 w-20" />
        </Card>
      ))}
    </LoadingRegion>
  );
}

export function TableSkeleton({
  rows = 8,
  columns = 5,
  label = 'Loading table',
  className,
}: LoadingProps & { rows?: number; columns?: number }) {
  return (
    <LoadingRegion label={label} className={cn('space-y-2', className)}>
      <Skeleton className="h-8 w-full" />
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="flex gap-3">
          {Array.from({ length: columns }, (_, c) => (
            <Skeleton key={c} className={cn('h-6', c === 0 ? 'w-1/3' : 'flex-1')} />
          ))}
        </div>
      ))}
    </LoadingRegion>
  );
}

export function ChartSkeleton({
  height = 280,
  label = 'Loading chart',
  className,
}: LoadingProps & { height?: number }) {
  return (
    <LoadingRegion label={label} className={className}>
      <div className="flex items-end gap-2" style={{ height }}>
        {[45, 70, 55, 85, 60, 75, 40, 65].map((h, i) => (
          <Skeleton key={i} className="flex-1" style={{ height: `${h}%` }} />
        ))}
      </div>
    </LoadingRegion>
  );
}

export function PageSkeleton({ label = 'Loading page' }: LoadingProps) {
  return (
    <LoadingRegion label={label} className="space-y-6">
      <div className="space-y-2">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-8 w-72" />
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="h-20" />
        ))}
      </div>
      <Card>
        <CardHeader>
          <Skeleton className="h-5 w-48" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-64 w-full" />
        </CardContent>
      </Card>
    </LoadingRegion>
  );
}
