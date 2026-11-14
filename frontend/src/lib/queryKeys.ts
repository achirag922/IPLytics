/**
 * Query-key factory. Keys are hierarchical (domain, resource, id, params) so related queries can be invalidated
 * together, and equal parameters always produce equal keys (no duplicate requests).
 */
export const queryKeys = {
  overview: () => ['overview'] as const,
  seasons: {
    all: () => ['seasons'] as const,
    summary: (year: number, params: object) => ['seasons', year, 'summary', params] as const,
    pointsTable: (year: number) => ['seasons', year, 'points-table'] as const,
  },
  teams: {
    all: () => ['teams'] as const,
    detail: (id: number) => ['teams', id] as const,
    resource: (id: number, resource: string, params: object) => ['teams', id, resource, params] as const,
  },
  players: {
    list: (params: object) => ['players', 'list', params] as const,
    detail: (id: number) => ['players', id] as const,
    resource: (id: number, resource: string, params: object) => ['players', id, resource, params] as const,
    compare: (params: object) => ['players', 'compare', params] as const,
  },
  matches: {
    list: (params: object) => ['matches', 'list', params] as const,
    detail: (id: number) => ['matches', id] as const,
    resource: (id: number, resource: string) => ['matches', id, resource] as const,
  },
  venues: {
    all: () => ['venues'] as const,
    detail: (id: number) => ['venues', id] as const,
    resource: (id: number, resource: string, params: object) => ['venues', id, resource, params] as const,
  },
  leaderboards: (discipline: string, params: object) => ['leaderboards', discipline, params] as const,
  headToHead: (params: object) => ['head-to-head', params] as const,
  matchup: (params: object) => ['matchups', params] as const,
};

/** IPL data only changes when an ingestion runs, so it stays fresh for a long time. */
export const STALE_TIME = {
  /** Seasons, teams, venues: change only when a new season is loaded. */
  reference: 30 * 60 * 1000,
  /** Statistics: change after an analytics refresh. */
  statistics: 10 * 60 * 1000,
} as const;
