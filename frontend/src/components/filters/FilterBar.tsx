import { RotateCcw } from 'lucide-react';
import { useId, type ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { Label, Select } from '@/components/ui/form';
import { cn } from '@/lib/utils';

interface FilterBarProps {
  children: ReactNode;
  /** Clears the page's filters; the button is hidden when not provided. */
  onReset?: () => void;
  resetDisabled?: boolean;
  label?: string;
  className?: string;
}

/** Groups a page's filters. Each filter writes to the URL, so the bar has no submit button. */
export function FilterBar({
  children,
  onReset,
  resetDisabled,
  label = 'Filters',
  className,
}: FilterBarProps) {
  return (
    <form
      role="search"
      aria-label={label}
      onSubmit={(event) => event.preventDefault()}
      className={cn('mb-6 flex flex-wrap items-end gap-3 rounded-lg border bg-card p-3 shadow-sm', className)}
    >
      {children}
      {onReset && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={onReset}
          disabled={resetDisabled}
          className="ml-auto"
        >
          <RotateCcw aria-hidden="true" />
          Reset
        </Button>
      )}
    </form>
  );
}

export interface Option {
  value: string;
  label: string;
}

interface SelectFilterProps {
  label: string;
  value: string | undefined;
  options: Option[];
  onChange: (value: string | undefined) => void;
  /** Label of the "no filter" choice; omit to make a choice required. */
  allLabel?: string;
  disabled?: boolean;
  className?: string;
}

/** A labelled select; the empty choice means "no filter". */
export function SelectFilter({
  label,
  value,
  options,
  onChange,
  allLabel,
  disabled,
  className,
}: SelectFilterProps) {
  const id = useId();
  return (
    <div className={cn('flex min-w-36 flex-col gap-1', className)}>
      <Label htmlFor={id}>{label}</Label>
      <Select
        id={id}
        value={value ?? ''}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value === '' ? undefined : event.target.value)}
      >
        {allLabel !== undefined && <option value="">{allLabel}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </Select>
    </div>
  );
}
