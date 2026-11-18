import { useMemo } from 'react';
import { Button } from '@/components/ui/button';
import { useSeasonsQuery } from '@/hooks/useSeasonQueries';
import type { StatsFilterState } from '@/hooks/useStatsFilters';
import { useTeamLookup } from '@/hooks/useTeamQueries';
import { useVenuesQuery } from '@/hooks/useVenueQueries';
import { ActiveFilterChips, type ActiveFilter } from './ActiveFilterChips';
import { SeasonFilter, SeasonRangeFilter, TeamFilter, VenueFilter } from './EntityFilters';
import { FilterBar } from './FilterBar';

interface StatsFilterBarProps {
  state: StatsFilterState;
  /** Which optional filters the page supports. */
  show?: { team?: boolean; opponent?: boolean; venue?: boolean; range?: boolean };
  /** Codes not offered as opponent (e.g. the team itself). */
  excludeOpponents?: string[];
}

const KEYS = ['season', 'seasonFrom', 'seasonTo', 'team', 'opponent', 'venue'] as const;
/** Quick ranges ending at the latest season. */
const PRESETS = [
  { label: 'Last season', seasons: 1 },
  { label: 'Last 3', seasons: 3 },
  { label: 'Last 5', seasons: 5 },
] as const;

/**
 * The shared statistics filters: one season or a season range (choosing one clears the other), quick ranges, plus
 * franchise, opponent and venue where the page supports them. Active filters show as removable chips. Every change
 * is written to the URL.
 */
export function StatsFilterBar({ state, show = {}, excludeOpponents }: StatsFilterBarProps) {
  const { filters, url } = state;
  const { range = true } = show;
  const active = KEYS.some((key) => url.get(key) !== undefined);
  const seasons = useSeasonsQuery();
  const teams = useTeamLookup();
  const venues = useVenuesQuery();
  const latest = useMemo(
    () => Math.max(0, ...(seasons.data ?? []).map((s) => s.year)) || undefined,
    [seasons.data],
  );

  const chips: ActiveFilter[] = [];
  if (filters.season !== undefined)
    chips.push({ key: 'season', label: 'Season', value: String(filters.season) });
  if (filters.season === undefined && filters.seasonFrom !== undefined)
    chips.push({ key: 'seasonFrom', label: 'From', value: String(filters.seasonFrom) });
  if (filters.season === undefined && filters.seasonTo !== undefined)
    chips.push({ key: 'seasonTo', label: 'To', value: String(filters.seasonTo) });
  if (filters.team)
    chips.push({ key: 'team', label: 'Team', value: teams.byCode(filters.team)?.name ?? filters.team });
  if (filters.opponent)
    chips.push({
      key: 'opponent',
      label: 'Opponent',
      value: teams.byCode(filters.opponent)?.name ?? filters.opponent,
    });
  if (filters.venue !== undefined)
    chips.push({
      key: 'venue',
      label: 'Venue',
      value: venues.data?.find((v) => v.id === filters.venue)?.name ?? `#${filters.venue}`,
    });

  return (
    <FilterBar
      onReset={() => url.update(Object.fromEntries(KEYS.map((k) => [k, undefined])))}
      resetDisabled={!active}
    >
      <SeasonFilter
        value={filters.season}
        onChange={(season) => url.update({ season, seasonFrom: undefined, seasonTo: undefined })}
      />
      {range && (
        <SeasonRangeFilter
          from={filters.season === undefined ? filters.seasonFrom : undefined}
          to={filters.season === undefined ? filters.seasonTo : undefined}
          onChange={({ from, to }) => url.update({ season: undefined, seasonFrom: from, seasonTo: to })}
        />
      )}
      {range && latest !== undefined && (
        <div role="group" aria-label="Quick season ranges" className="flex items-end gap-1">
          {PRESETS.map((p) => (
            <Button
              key={p.label}
              variant="ghost"
              size="sm"
              onClick={() =>
                url.update(
                  p.seasons === 1
                    ? { season: latest, seasonFrom: undefined, seasonTo: undefined }
                    : { season: undefined, seasonFrom: latest - p.seasons + 1, seasonTo: latest },
                )
              }
            >
              {p.label}
            </Button>
          ))}
        </div>
      )}
      {show.team && <TeamFilter value={filters.team} onChange={(team) => url.update({ team })} />}
      {show.opponent && (
        <TeamFilter
          label="Opponent"
          allLabel="All opponents"
          value={filters.opponent}
          exclude={excludeOpponents}
          onChange={(opponent) => url.update({ opponent })}
        />
      )}
      {show.venue && <VenueFilter value={filters.venue} onChange={(venue) => url.update({ venue })} />}
      <ActiveFilterChips filters={chips} onRemove={(key) => url.update({ [key]: undefined })} />
      {state.unknownTeamCode && (
        <p role="status" className="basis-full text-sm text-destructive">
          No franchise has the code “{state.unknownTeamCode}”; that filter is ignored.
        </p>
      )}
    </FilterBar>
  );
}
