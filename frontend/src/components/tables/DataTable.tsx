import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { EmptyState } from '@/components/states/EmptyState';
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { cn } from '@/lib/utils';

export interface Column<T> {
  key: string;
  header: string;
  /** Full name of an abbreviated header (M, W, NRR), shown as a tooltip and read by screen readers. */
  title?: string;
  cell: (row: T, index: number) => ReactNode;
  align?: 'left' | 'right' | 'center';
  className?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string | number;
  /** Describes the table for assistive technology (rendered visually hidden unless showCaption). */
  caption: string;
  showCaption?: boolean;
  /** Clicking a row navigates here; keyboard users follow the link rendered in the row. */
  rowHref?: (row: T) => string;
  emptyTitle?: string;
  emptyDescription?: string;
  className?: string;
}

const alignClass = { left: 'text-left', right: 'text-right', center: 'text-center' } as const;

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  caption,
  showCaption = false,
  rowHref,
  emptyTitle = 'No data available',
  emptyDescription,
  className,
}: DataTableProps<T>) {
  const navigate = useNavigate();
  if (rows.length === 0) return <EmptyState title={emptyTitle} description={emptyDescription} />;
  return (
    <Table className={className}>
      <TableCaption className={showCaption ? undefined : 'sr-only'}>{caption}</TableCaption>
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          {columns.map((column) => (
            <TableHead
              key={column.key}
              scope="col"
              className={cn(alignClass[column.align ?? 'left'], column.className)}
            >
              {column.title ? (
                <abbr title={column.title} className="no-underline">
                  {column.header}
                </abbr>
              ) : (
                column.header
              )}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row, index) => {
          const href = rowHref?.(row);
          return (
            <TableRow
              key={rowKey(row, index)}
              className={href ? 'cursor-pointer' : undefined}
              onClick={
                href
                  ? (event) => {
                      if ((event.target as HTMLElement).closest('a,button')) return;
                      navigate(href);
                    }
                  : undefined
              }
            >
              {columns.map((column) => (
                <TableCell
                  key={column.key}
                  className={cn(alignClass[column.align ?? 'left'], 'tabular-nums', column.className)}
                >
                  {column.cell(row, index)}
                </TableCell>
              ))}
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
