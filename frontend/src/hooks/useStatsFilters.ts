import { useMemo } from 'react';
import { useTeamLookup } from './useTeamQueries';
import { useUrlState, type UrlState } from './useUrlState';

/** The statistics filters shared by many pages, as they appear in the URL (teams by code: ?opponent=CSK). */
export interface StatsFilters {
  season?: number;
  seasonFrom?: number;
  seasonTo?: number;
  team?: string;
  opponent?: string;
  venue?: number;
}

/** The same filters as API query parameters (teams by id). */
export interface StatsQuery {
  season?: number;
  seasonFrom?: number;
  seasonTo?: number;
  team?: number;
  opponent?: number;
  venue?: number;
}

export interface StatsFilterState {
  filters: StatsFilters;
  /** API parameters; undefined until team codes are resolved (queries wait for it). */
  query: StatsQuery | undefined;
  /** A team code in the URL that matches no franchise. */
  unknownTeamCode?: string;
  url: UrlState;
}

/**
 * Reads the shared filters from the URL and turns them into API parameters. Team codes are resolved through the
 * cached team list, so URLs stay readable (/players/42/batting?season=2026&opponent=CSK).
 */
export function useStatsFilters(): StatsFilterState {
  const url = useUrlState();
  const lookup = useTeamLookup();
  const season = url.getNumber('season');
  const seasonFrom = url.getNumber('seasonFrom');
  const seasonTo = url.getNumber('seasonTo');
  const team = url.get('team')?.toUpperCase();
  const opponent = url.get('opponent')?.toUpperCase();
  const venue = url.getNumber('venue');

  return useMemo(() => {
    const filters: StatsFilters = { season, seasonFrom, seasonTo, team, opponent, venue };
    const needsTeams = team !== undefined || opponent !== undefined;
    if (needsTeams && lookup.isLoading) return { filters, query: undefined, url };
    const unknownTeamCode = [team, opponent].find((code) => code !== undefined && !lookup.byCode(code));
    const query: StatsQuery = {
      // A single season wins over a range, matching the API rule that the two are not combined.
      season,
      seasonFrom: season === undefined ? seasonFrom : undefined,
      seasonTo: season === undefined ? seasonTo : undefined,
      team: lookup.byCode(team)?.id,
      opponent: lookup.byCode(opponent)?.id,
      venue,
    };
    return { filters, query, unknownTeamCode, url };
  }, [season, seasonFrom, seasonTo, team, opponent, venue, lookup, url]);
}
