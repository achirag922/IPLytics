import type { UseQueryResult } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';

interface QueryContentProps<T> {
  query: UseQueryResult<T>;
  /** Skeleton shown while the first response loads. */
  loading: ReactNode;
  /** When true for the data, the empty state is shown instead of the content. */
  isEmpty?: (data: T) => boolean;
  emptyTitle?: string;
  emptyDescription?: string;
  /** Rendered while the query is disabled (e.g. waiting for the user to pick filters). */
  idle?: ReactNode;
  children: (data: T) => ReactNode;
}

/** Renders the loading, error, empty or success state of one query, consistently across pages. */
export function QueryContent<T>({
  query,
  loading,
  isEmpty,
  emptyTitle = 'No data available',
  emptyDescription,
  idle = null,
  children,
}: QueryContentProps<T>) {
  if (query.isPending && query.fetchStatus === 'idle') return <>{idle}</>;
  if (query.isPending) return <>{loading}</>;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  if (isEmpty?.(query.data)) return <EmptyState title={emptyTitle} description={emptyDescription} />;
  return <>{children(query.data)}</>;
}
