import { Download } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface ExportCsvButtonProps {
  onExport: () => void;
  label?: string;
  busy?: boolean;
  disabled?: boolean;
}

export function ExportCsvButton({
  onExport,
  label = 'Export CSV',
  busy = false,
  disabled = false,
}: ExportCsvButtonProps) {
  return (
    <Button variant="outline" size="sm" onClick={onExport} disabled={disabled || busy} aria-busy={busy}>
      <Download aria-hidden="true" />
      {busy ? 'Exporting…' : label}
    </Button>
  );
}
