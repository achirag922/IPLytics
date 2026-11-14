import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { playersApi, type PlayerListQuery, type PlayerStatsQuery } from '@/api/playersApi';
import { queryKeys } from '@/lib/queryKeys';

/** Server-side paged player list; the previous page stays visible while the next one loads. */
export function usePlayersQuery(query: PlayerListQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.players.list(query ?? {}),
    queryFn: ({ signal }) => playersApi.list(query ?? {}, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

export function usePlayerQuery(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.players.detail(id ?? 0),
    queryFn: ({ signal }) => playersApi.get(id as number, signal),
    enabled: id !== undefined,
  });
}

export function usePlayerBattingQuery(id: number | undefined, query: PlayerStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.players.resource(id ?? 0, 'batting', query ?? {}),
    queryFn: ({ signal }) => playersApi.batting(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function usePlayerBowlingQuery(id: number | undefined, query: PlayerStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.players.resource(id ?? 0, 'bowling', query ?? {}),
    queryFn: ({ signal }) => playersApi.bowling(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function usePlayerPhasesQuery(id: number | undefined, query: PlayerStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.players.resource(id ?? 0, 'phases', query ?? {}),
    queryFn: ({ signal }) => playersApi.phases(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function usePlayerVsTeamsQuery(id: number | undefined, query: PlayerStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.players.resource(id ?? 0, 'vs-teams', query ?? {}),
    queryFn: ({ signal }) => playersApi.vsTeams(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function usePlayerVenuesQuery(id: number | undefined, query: PlayerStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.players.resource(id ?? 0, 'venues', query ?? {}),
    queryFn: ({ signal }) => playersApi.venues(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}
