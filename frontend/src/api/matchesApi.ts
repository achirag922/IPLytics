import { getData, getPage } from './client/request';
import type { QueryOf, Schemas } from './client/types';

export type MatchSummary = Schemas['MatchSummaryDto'];
export type MatchDetail = Schemas['MatchDetailDto'];
export type Scorecard = Schemas['ScorecardDto'];
export type InningsScorecard = Schemas['InningsScorecardDto'];
export type MatchProgression = Schemas['MatchProgressionDto'];
export type InningsProgression = Schemas['InningsProgressionDto'];
export type MatchListQuery = QueryOf<'/api/v1/matches'>;

export const matchesApi = {
  list: (query: MatchListQuery, signal?: AbortSignal) => getPage<MatchSummary>('/matches', query, signal),
  get: (id: number, signal?: AbortSignal) => getData<MatchDetail>(`/matches/${id}`, undefined, signal),
  scorecard: (id: number, signal?: AbortSignal) =>
    getData<Scorecard>(`/matches/${id}/scorecard`, undefined, signal),
  progression: (id: number, signal?: AbortSignal) =>
    getData<MatchProgression>(`/matches/${id}/progression`, undefined, signal),
};
