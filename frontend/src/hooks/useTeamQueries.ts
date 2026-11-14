import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { teamsApi, type Team, type TeamStatsQuery, type TopPerformersQuery } from '@/api/teamsApi';
import { queryKeys, STALE_TIME } from '@/lib/queryKeys';

export function useTeamsQuery() {
  return useQuery({
    queryKey: queryKeys.teams.all(),
    queryFn: ({ signal }) => teamsApi.list(signal),
    staleTime: STALE_TIME.reference,
  });
}

export function useTeamQuery(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.teams.detail(id ?? 0),
    queryFn: ({ signal }) => teamsApi.get(id as number, signal),
    enabled: id !== undefined,
    staleTime: STALE_TIME.reference,
  });
}

/** Team statistics are fetched once the query parameters are resolved (team codes in the URL -> ids). */
export function useTeamStatsQuery(id: number | undefined, query: TeamStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.teams.resource(id ?? 0, 'stats', query ?? {}),
    queryFn: ({ signal }) => teamsApi.stats(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function useTeamSeasonsQuery(id: number | undefined, query: TeamStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.teams.resource(id ?? 0, 'seasons', query ?? {}),
    queryFn: ({ signal }) => teamsApi.seasons(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function useTeamVenuesQuery(id: number | undefined, query: TeamStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.teams.resource(id ?? 0, 'venues', query ?? {}),
    queryFn: ({ signal }) => teamsApi.venues(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function useTeamOpponentsQuery(id: number | undefined, query: TeamStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.teams.resource(id ?? 0, 'opponents', query ?? {}),
    queryFn: ({ signal }) => teamsApi.opponents(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function useTeamTopPerformersQuery(id: number | undefined, query: TopPerformersQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.teams.resource(id ?? 0, 'top-performers', query ?? {}),
    queryFn: ({ signal }) => teamsApi.topPerformers(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export interface TeamLookup {
  teams: Team[];
  byCode: (code: string | null | undefined) => Team | undefined;
  byId: (id: number | null | undefined) => Team | undefined;
  isLoading: boolean;
  isError: boolean;
}

/** Resolves team codes used in URLs (?team=MI) to API ids, from the cached team list. */
export function useTeamLookup(): TeamLookup {
  const { data, isLoading, isError } = useTeamsQuery();
  return useMemo(() => {
    const teams = data ?? [];
    const codes = new Map(teams.map((t) => [t.code?.toUpperCase(), t]));
    const ids = new Map(teams.map((t) => [t.id, t]));
    return {
      teams,
      byCode: (code) => (code ? codes.get(code.toUpperCase()) : undefined),
      byId: (id) => (id === null || id === undefined ? undefined : ids.get(id)),
      isLoading,
      isError,
    };
  }, [data, isLoading, isError]);
}
