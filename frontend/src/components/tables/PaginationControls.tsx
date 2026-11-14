import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { Pagination } from '@/api/client';
import { Button } from '@/components/ui/button';
import { formatInteger } from '@/lib/format';

interface PaginationControlsProps {
  pagination: Pagination;
  onPageChange: (page: number) => void;
  /** True while the next page is loading (the current one stays visible). */
  isFetching?: boolean;
  itemLabel?: string;
}

/** Server-side pagination: pages are zero-based in the API and shown one-based. */
export function PaginationControls({
  pagination,
  onPageChange,
  isFetching,
  itemLabel = 'results',
}: PaginationControlsProps) {
  const { page, size, totalElements, totalPages } = pagination;
  if (totalElements === 0) return null;
  const first = page * size + 1;
  const last = Math.min(totalElements, (page + 1) * size);
  return (
    <nav aria-label="Pagination" className="flex flex-wrap items-center justify-between gap-3 pt-3 text-sm">
      <p className="text-muted-foreground" aria-live="polite">
        {formatInteger(first)}–{formatInteger(last)} of {formatInteger(totalElements)} {itemLabel}
        {isFetching ? ' (updating…)' : ''}
      </p>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
          <ChevronLeft aria-hidden="true" />
          Previous
        </Button>
        <span className="tabular-nums text-muted-foreground">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= totalPages}
        >
          Next
          <ChevronRight aria-hidden="true" />
        </Button>
      </div>
    </nav>
  );
}
