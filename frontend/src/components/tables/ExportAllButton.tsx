import { MAX_EXPORT_ROWS } from '@/hooks/useExportAll';
import { formatInteger } from '@/lib/format';
import { ExportCsvButton } from './ExportCsvButton';

interface ExportAllButtonProps {
  exporter: { exportAll: () => Promise<void>; exporting: boolean; error: boolean };
  /** Rows matching the filters (from the pagination block). */
  total: number;
}

/** "Export all N" for a server-paged list, capped at {@link MAX_EXPORT_ROWS} rows. */
export function ExportAllButton({ exporter, total }: ExportAllButtonProps) {
  const rows = Math.min(total, MAX_EXPORT_ROWS);
  return (
    <>
      {exporter.error && (
        <span role="alert" className="text-sm text-destructive">
          Export failed. Please try again.
        </span>
      )}
      {total > MAX_EXPORT_ROWS && (
        <span className="text-xs text-muted-foreground">
          First {formatInteger(MAX_EXPORT_ROWS)} rows; narrow the filters for the rest.
        </span>
      )}
      <ExportCsvButton
        label={`Export all ${formatInteger(rows)}`}
        onExport={() => void exporter.exportAll()}
        busy={exporter.exporting}
        disabled={total === 0}
      />
    </>
  );
}
