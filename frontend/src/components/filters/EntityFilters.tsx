import { useId, useMemo } from 'react';
import { Input, Label } from '@/components/ui/form';
import { useSeasonsQuery } from '@/hooks/useSeasonQueries';
import { useTeamsQuery } from '@/hooks/useTeamQueries';
import { useVenuesQuery } from '@/hooks/useVenueQueries';
import { SelectFilter, type Option } from './FilterBar';

function useSeasonOptions(): Option[] {
  const { data } = useSeasonsQuery();
  return useMemo(
    () =>
      [...(data ?? [])]
        .sort((a, b) => b.year - a.year)
        .map((s) => ({ value: String(s.year), label: String(s.year) })),
    [data],
  );
}

interface SeasonFilterProps {
  value: number | undefined;
  onChange: (season: number | undefined) => void;
  /** When false the filter always has a season selected. */
  allowAll?: boolean;
  label?: string;
}

export function SeasonFilter({ value, onChange, allowAll = true, label = 'Season' }: SeasonFilterProps) {
  const options = useSeasonOptions();
  return (
    <SelectFilter
      label={label}
      value={value === undefined ? undefined : String(value)}
      options={options}
      allLabel={allowAll ? 'All seasons' : undefined}
      onChange={(v) => onChange(v === undefined ? undefined : Number(v))}
    />
  );
}

interface SeasonRangeFilterProps {
  from: number | undefined;
  to: number | undefined;
  onChange: (range: { from: number | undefined; to: number | undefined }) => void;
}

/** First and last season (inclusive). Choices that would make an empty range are disabled. */
export function SeasonRangeFilter({ from, to, onChange }: SeasonRangeFilterProps) {
  const options = useSeasonOptions();
  return (
    <>
      <SelectFilter
        label="From season"
        value={from === undefined ? undefined : String(from)}
        options={options.filter((o) => to === undefined || Number(o.value) <= to)}
        allLabel="Earliest"
        onChange={(v) => onChange({ from: v === undefined ? undefined : Number(v), to })}
      />
      <SelectFilter
        label="To season"
        value={to === undefined ? undefined : String(to)}
        options={options.filter((o) => from === undefined || Number(o.value) >= from)}
        allLabel="Latest"
        onChange={(v) => onChange({ from, to: v === undefined ? undefined : Number(v) })}
      />
    </>
  );
}

interface TeamFilterProps {
  /** Team code, as used in URLs. */
  value: string | undefined;
  onChange: (code: string | undefined) => void;
  label?: string;
  allLabel?: string;
  /** Codes that cannot be chosen (e.g. the other side of a head-to-head). */
  exclude?: string[];
}

export function TeamFilter({
  value,
  onChange,
  label = 'Team',
  allLabel = 'All teams',
  exclude = [],
}: TeamFilterProps) {
  const { data } = useTeamsQuery();
  const options = useMemo(
    () =>
      [...(data ?? [])]
        .filter((t) => t.code && !exclude.includes(t.code))
        .sort((a, b) => Number(b.active) - Number(a.active) || (a.name ?? '').localeCompare(b.name ?? ''))
        .map((t) => ({ value: t.code ?? '', label: t.active ? (t.name ?? '') : `${t.name} (defunct)` })),
    [data, exclude],
  );
  return (
    <SelectFilter label={label} value={value} options={options} allLabel={allLabel} onChange={onChange} />
  );
}

interface VenueFilterProps {
  value: number | undefined;
  onChange: (venueId: number | undefined) => void;
}

export function VenueFilter({ value, onChange }: VenueFilterProps) {
  const { data } = useVenuesQuery();
  const options = useMemo(
    () =>
      [...(data ?? [])]
        .sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''))
        .map((v) => ({ value: String(v.id), label: v.city ? `${v.name}, ${v.city}` : (v.name ?? '') })),
    [data],
  );
  return (
    <SelectFilter
      label="Venue"
      value={value === undefined ? undefined : String(value)}
      options={options}
      allLabel="All venues"
      onChange={(v) => onChange(v === undefined ? undefined : Number(v))}
      className="min-w-48 max-w-72"
    />
  );
}

interface DateRangeFilterProps {
  from: string | undefined;
  to: string | undefined;
  onChange: (range: { from: string | undefined; to: string | undefined }) => void;
}

/** ISO dates (yyyy-MM-dd); the browser's date picker keeps them valid. */
export function DateRangeFilter({ from, to, onChange }: DateRangeFilterProps) {
  const fromId = useId();
  const toId = useId();
  return (
    <>
      <div className="flex flex-col gap-1">
        <Label htmlFor={fromId}>From date</Label>
        <Input
          id={fromId}
          type="date"
          value={from ?? ''}
          max={to}
          onChange={(e) => onChange({ from: e.target.value || undefined, to })}
        />
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor={toId}>To date</Label>
        <Input
          id={toId}
          type="date"
          value={to ?? ''}
          min={from}
          onChange={(e) => onChange({ from, to: e.target.value || undefined })}
        />
      </div>
    </>
  );
}
