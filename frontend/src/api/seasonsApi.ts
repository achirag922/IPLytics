import { getData, getList } from './client/request';
import type { QueryOf, Schemas } from './client/types';

export type Season = Schemas['SeasonDto'];
export type SeasonSummary = Schemas['SeasonSummaryDto'];
export type PointsTable = Schemas['PointsTableDto'];
export type PointsTableRow = Schemas['PointsTableRowDto'];
export type SeasonSummaryQuery = QueryOf<'/api/v1/seasons/{year}/summary'>;

export const seasonsApi = {
  list: (signal?: AbortSignal) => getList<Season>('/seasons', undefined, signal),
  summary: (year: number, query: SeasonSummaryQuery = {}, signal?: AbortSignal) =>
    getData<SeasonSummary>(`/seasons/${year}/summary`, query, signal),
  pointsTable: (year: number, signal?: AbortSignal) =>
    getData<PointsTable>(`/seasons/${year}/points-table`, undefined, signal),
};
