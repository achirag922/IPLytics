import { X } from 'lucide-react';

export interface ActiveFilter {
  key: string;
  label: string;
  value: string;
}

interface ActiveFilterChipsProps {
  filters: ActiveFilter[];
  onRemove: (key: string) => void;
}

/** The filters in force, each removable on its own; shows nothing when no filter is set. */
export function ActiveFilterChips({ filters, onRemove }: ActiveFilterChipsProps) {
  if (filters.length === 0) return null;
  return (
    <ul aria-label="Active filters" className="flex basis-full flex-wrap gap-2">
      {filters.map((f) => (
        <li key={f.key}>
          <span className="inline-flex items-center gap-1 rounded-full border bg-secondary py-0.5 pl-2.5 pr-1 text-xs">
            <span className="text-muted-foreground">{f.label}:</span>
            <span className="font-medium">{f.value}</span>
            <button
              type="button"
              onClick={() => onRemove(f.key)}
              aria-label={`Remove filter ${f.label}: ${f.value}`}
              className="grid size-5 place-items-center rounded-full hover:bg-accent"
            >
              <X aria-hidden="true" className="size-3" />
            </button>
          </span>
        </li>
      ))}
    </ul>
  );
}
