import { getData, getPage } from './client/request';
import type { QueryOf, Schemas } from './client/types';

export type PlayerListItem = Schemas['PlayerListItemDto'];
export type PlayerDetail = Schemas['PlayerDetailDto'];
export type PlayerBatting = Schemas['PlayerBattingDto'];
export type PlayerBowling = Schemas['PlayerBowlingDto'];
export type PlayerPhases = Schemas['PlayerPhasesDto'];
export type PlayerVsTeams = Schemas['PlayerVsTeamsDto'];
export type PlayerVenues = Schemas['PlayerVenuesDto'];
export type BattingSummary = Schemas['BattingSummaryDto'];
export type BowlingSummary = Schemas['BowlingSummaryDto'];
export type PlayerRef = Schemas['PlayerRef'];
export type PlayerListQuery = QueryOf<'/api/v1/players'>;
/** Filters accepted by the player statistics endpoints (season range, opponent, venue). */
export type PlayerStatsQuery = QueryOf<'/api/v1/players/{id}/batting'>;

export const playersApi = {
  list: (query: PlayerListQuery, signal?: AbortSignal) => getPage<PlayerListItem>('/players', query, signal),
  get: (id: number, signal?: AbortSignal) => getData<PlayerDetail>(`/players/${id}`, undefined, signal),
  batting: (id: number, query: PlayerStatsQuery, signal?: AbortSignal) =>
    getData<PlayerBatting>(`/players/${id}/batting`, query, signal),
  bowling: (id: number, query: PlayerStatsQuery, signal?: AbortSignal) =>
    getData<PlayerBowling>(`/players/${id}/bowling`, query, signal),
  phases: (id: number, query: PlayerStatsQuery, signal?: AbortSignal) =>
    getData<PlayerPhases>(`/players/${id}/phases`, query, signal),
  vsTeams: (id: number, query: PlayerStatsQuery, signal?: AbortSignal) =>
    getData<PlayerVsTeams>(`/players/${id}/vs-teams`, query, signal),
  venues: (id: number, query: PlayerStatsQuery, signal?: AbortSignal) =>
    getData<PlayerVenues>(`/players/${id}/venues`, query, signal),
};
