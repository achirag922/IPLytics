import { useOutletContext } from 'react-router';
import type { MatchDetail } from '@/api/matchesApi';
import type { PlayerDetail } from '@/api/playersApi';
import type { Team } from '@/api/teamsApi';
import type { StatsFilters, StatsQuery } from '@/hooks/useStatsFilters';
import type { UrlState } from '@/hooks/useUrlState';

/**
 * What a detail page (team, player, match) hands to its tab routes through the router outlet: the loaded entity
 * and, for statistics tabs, the API filters read from the URL (undefined while team codes resolve).
 */
export interface TeamContext {
  team: Team;
  query: StatsQuery | undefined;
}

export interface PlayerContext {
  player: PlayerDetail;
  query: StatsQuery | undefined;
}

export interface MatchContext {
  match: MatchDetail;
}

export const useTeamContext = () => useOutletContext<TeamContext>();
export const usePlayerContext = () => useOutletContext<PlayerContext>();
export const useMatchContext = () => useOutletContext<MatchContext>();

/** The insights hub hands its URL filters to its tabs. */
export interface InsightsContext {
  query: StatsQuery | undefined;
  filters: StatsFilters;
  url: UrlState;
}

export const useInsightsContext = () => useOutletContext<InsightsContext>();
