import { AlertTriangle, RotateCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { describeError } from '@/lib/errors';
import { cn } from '@/lib/utils';

interface ErrorStateProps {
  error?: unknown;
  onRetry?: () => void;
  className?: string;
}

/** "Unable to load data." with a friendly reason and a retry button; API error details stay hidden. */
export function ErrorState({ error, onRetry, className }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-6 py-8 text-center',
        className,
      )}
    >
      <AlertTriangle aria-hidden="true" className="size-7 text-destructive" />
      <p className="font-medium">Unable to load data.</p>
      <p className="max-w-md text-sm text-muted-foreground">{describeError(error)}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry} className="mt-1">
          <RotateCw aria-hidden="true" />
          Retry
        </Button>
      )}
    </div>
  );
}
