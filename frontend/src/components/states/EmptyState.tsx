import { SearchX } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface EmptyStateProps {
  title?: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

/** Shown instead of a table or chart when there is nothing to display; never a broken chart. */
export function EmptyState({ title = 'No data available', description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed bg-muted/20 px-6 py-12 text-center',
        className,
      )}
    >
      <span aria-hidden="true" className="mb-1 grid size-12 place-items-center rounded-full bg-muted">
        <SearchX className="size-6 text-muted-foreground" />
      </span>
      <p className="font-medium">{title}</p>
      {description && <p className="max-w-md text-sm text-muted-foreground">{description}</p>}
      {action}
    </div>
  );
}
