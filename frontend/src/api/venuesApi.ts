import { getData, getList } from './client/request';
import type { QueryOf, Schemas } from './client/types';

export type Venue = Schemas['VenueDto'];
export type VenueStats = Schemas['VenueStatsDto'];
export type VenueSeasonStats = Schemas['VenueSeasonStatsDto'];
export type ScoreBand = Schemas['ScoreBandDto'];
export type VenueStatsQuery = QueryOf<'/api/v1/venues/{id}/stats'>;

export const venuesApi = {
  list: (signal?: AbortSignal) => getList<Venue>('/venues', undefined, signal),
  get: (id: number, signal?: AbortSignal) => getData<Venue>(`/venues/${id}`, undefined, signal),
  stats: (id: number, query: VenueStatsQuery, signal?: AbortSignal) =>
    getData<VenueStats>(`/venues/${id}/stats`, query, signal),
  seasons: (id: number, query: VenueStatsQuery, signal?: AbortSignal) =>
    getList<VenueSeasonStats>(`/venues/${id}/seasons`, query, signal),
  scoreDistribution: (id: number, query: VenueStatsQuery, signal?: AbortSignal) =>
    getList<ScoreBand>(`/venues/${id}/score-distribution`, query, signal),
};
