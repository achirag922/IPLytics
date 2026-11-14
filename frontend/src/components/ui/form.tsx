import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

const controlClass =
  'h-9 w-full rounded-md border border-input bg-card px-3 text-sm shadow-sm transition-colors placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50';

export function Input({ className, type = 'text', ...props }: ComponentProps<'input'>) {
  return <input type={type} className={cn(controlClass, className)} {...props} />;
}

/** Native select styled like shadcn/ui: keyboard and screen-reader behaviour come from the browser. */
export function Select({ className, ...props }: ComponentProps<'select'>) {
  return <select className={cn(controlClass, 'pr-8', className)} {...props} />;
}

export function Label({ className, ...props }: ComponentProps<'label'>) {
  return <label className={cn('text-xs font-medium text-muted-foreground', className)} {...props} />;
}
