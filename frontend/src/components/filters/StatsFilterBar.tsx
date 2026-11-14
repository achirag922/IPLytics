import type { StatsFilterState } from '@/hooks/useStatsFilters';
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

/**
 * The shared statistics filters: one season or a season range (choosing one clears the other), plus franchise,
 * opponent and venue where the page supports them. Every change is written to the URL.
 */
export function StatsFilterBar({ state, show = {}, excludeOpponents }: StatsFilterBarProps) {
  const { filters, url } = state;
  const { range = true } = show;
  const active = KEYS.some((key) => url.get(key) !== undefined);
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
      {state.unknownTeamCode && (
        <p role="status" className="basis-full text-sm text-destructive">
          No franchise has the code “{state.unknownTeamCode}”; that filter is ignored.
        </p>
      )}
    </FilterBar>
  );
}
