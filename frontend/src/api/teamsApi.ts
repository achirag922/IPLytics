import { getData, getList } from './client/request';
import type { QueryOf, Schemas } from './client/types';

export type Team = Schemas['TeamDto'];
export type TeamRef = Schemas['TeamRef'];
export type TeamRecord = Schemas['TeamRecordDto'];
export type TeamStats = Schemas['TeamStatsDto'];
export type TeamSeasonStats = Schemas['TeamSeasonStatsDto'];
export type TeamVenueStats = Schemas['TeamVenueStatsDto'];
export type TeamOpponentStats = Schemas['TeamOpponentStatsDto'];
export type TopPerformers = Schemas['TopPerformersDto'];
/** Filters accepted by the team statistics endpoints (season range, opponent, venue). */
export type TeamStatsQuery = QueryOf<'/api/v1/teams/{id}/stats'>;
export type TopPerformersQuery = QueryOf<'/api/v1/teams/{id}/top-performers'>;

export const teamsApi = {
  list: (signal?: AbortSignal) => getList<Team>('/teams', undefined, signal),
  get: (id: number, signal?: AbortSignal) => getData<Team>(`/teams/${id}`, undefined, signal),
  stats: (id: number, query: TeamStatsQuery, signal?: AbortSignal) =>
    getData<TeamStats>(`/teams/${id}/stats`, query, signal),
  seasons: (id: number, query: TeamStatsQuery, signal?: AbortSignal) =>
    getList<TeamSeasonStats>(`/teams/${id}/seasons`, query, signal),
  venues: (id: number, query: TeamStatsQuery, signal?: AbortSignal) =>
    getList<TeamVenueStats>(`/teams/${id}/venues`, query, signal),
  opponents: (id: number, query: TeamStatsQuery, signal?: AbortSignal) =>
    getList<TeamOpponentStats>(`/teams/${id}/opponents`, query, signal),
  topPerformers: (id: number, query: TopPerformersQuery, signal?: AbortSignal) =>
    getData<TopPerformers>(`/teams/${id}/top-performers`, query, signal),
};
