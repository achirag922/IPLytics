import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import type { Page } from '@/api/client';
import { downloadCsv, toCsv, type CsvColumn } from '@/lib/csv';

/** Largest export; beyond it the user is asked to narrow the filters. */
export const MAX_EXPORT_ROWS = 2000;
const EXPORT_PAGE_SIZE = 100;

interface ExportAllOptions<T> {
  /** Cache key prefix of the list; each page is cached under it with its page number. */
  queryKey: readonly unknown[];
  fetchPage: (page: number, size: number, signal?: AbortSignal) => Promise<Page<T>>;
  columns: CsvColumn<T>[];
  fileName: string;
}

/**
 * Exports every row matching the current filters of a server-paged list, fetching pages of 100 through TanStack
 * Query (so pages already loaded are reused) up to {@link MAX_EXPORT_ROWS} rows.
 */
export function useExportAll<T>({ queryKey, fetchPage, columns, fileName }: ExportAllOptions<T>) {
  const queryClient = useQueryClient();
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState(false);

  const exportAll = useCallback(async () => {
    setExporting(true);
    setError(false);
    try {
      const rows: T[] = [];
      for (let page = 0; rows.length < MAX_EXPORT_ROWS; page++) {
        const result = await queryClient.fetchQuery({
          queryKey: [...queryKey, 'export', page],
          queryFn: ({ signal }) => fetchPage(page, EXPORT_PAGE_SIZE, signal),
        });
        rows.push(...result.items);
        if (page + 1 >= result.pagination.totalPages || result.items.length === 0) break;
      }
      downloadCsv(fileName, toCsv(columns, rows.slice(0, MAX_EXPORT_ROWS)));
    } catch {
      setError(true);
    } finally {
      setExporting(false);
    }
  }, [queryClient, queryKey, fetchPage, columns, fileName]);

  return { exportAll, exporting, error };
}
