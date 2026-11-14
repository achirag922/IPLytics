import { http, HttpResponse, type JsonBodyType } from 'msw';
import { setupServer } from 'msw/node';
import * as f from './fixtures';

/** The {data, meta} envelope; lists also get a one-page pagination block. */
export function envelope(data: JsonBodyType, pagination?: object) {
  const list = Array.isArray(data);
  return HttpResponse.json({
    data,
    pagination:
      pagination ??
      (list ? { page: 0, size: data.length, totalElements: data.length, totalPages: 1 } : undefined),
    meta: { generatedAt: '2026-11-14T10:00:00Z', dataVersion: 't1-a1' },
  });
}

export function problem(status: number, title = 'Error') {
  return HttpResponse.json(
    { type: 'about:blank', title, status, detail: `internal detail ${status}` },
    { status },
  );
}

const api = (path: string) => `*/api/v1${path}`;

/** Default happy-path API. Tests override single endpoints with server.use(...). */
export const handlers = [
  http.get(api('/overview'), () => envelope(f.overview)),
  http.get(api('/seasons'), () => envelope(f.seasons)),
  http.get(api('/seasons/:year/summary'), () => envelope(f.seasonSummary)),
  http.get(api('/seasons/:year/points-table'), () => envelope(f.pointsTable)),
  http.get(api('/teams'), () => envelope(f.teams)),
  http.get(api('/teams/:id'), ({ params }) => {
    const team = f.teams.find((t) => t.id === Number(params.id));
    return team ? envelope(team) : problem(404, 'Resource not found');
  }),
  http.get(api('/teams/:id/stats'), () => envelope(f.teamStats)),
  http.get(api('/teams/:id/seasons'), () => envelope(f.teamSeasons)),
  http.get(api('/teams/:id/venues'), () => envelope([{ venue: f.WANKHEDE, stats: f.teamStats }])),
  http.get(api('/teams/:id/opponents'), () => envelope([{ opponent: f.CSK, stats: f.teamStats }])),
  http.get(api('/teams/:id/top-performers'), () =>
    envelope({ batting: f.battingLeaders, bowling: f.bowlingLeaders }),
  ),
  http.get(api('/players'), ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? 20);
    return envelope(f.playerList, { page, size, totalElements: 60, totalPages: Math.ceil(60 / size) });
  }),
  http.get(api('/players/compare'), ({ request }) => {
    const discipline =
      new URL(request.url).searchParams.get('discipline') === 'bowling' ? 'bowling' : 'batting';
    return envelope(f.comparison(discipline));
  }),
  http.get(api('/players/:id'), ({ params }) =>
    Number(params.id) === f.playerDetail.id
      ? envelope(f.playerDetail)
      : envelope({ ...f.playerDetail, id: Number(params.id), name: `Player ${params.id}` }),
  ),
  http.get(api('/players/:id/batting'), () => envelope(f.playerBatting)),
  http.get(api('/players/:id/bowling'), () => envelope(f.playerBowling)),
  http.get(api('/players/:id/phases'), () => envelope(f.playerPhases)),
  http.get(api('/players/:id/vs-teams'), () => envelope(f.playerVsTeams)),
  http.get(api('/players/:id/venues'), () => envelope(f.playerVenues)),
  http.get(api('/matches'), ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    return envelope([f.matchSummary(77), f.matchSummary(78, { season: 2018, date: '2018-05-01' })], {
      page,
      size: 25,
      totalElements: 60,
      totalPages: 3,
    });
  }),
  http.get(api('/matches/:id'), () => envelope(f.matchDetail)),
  http.get(api('/matches/:id/scorecard'), () => envelope(f.scorecard)),
  http.get(api('/matches/:id/progression'), () => envelope(f.progression)),
  http.get(api('/venues'), () => envelope(f.venues)),
  http.get(api('/venues/:id'), () => envelope(f.venues[0] ?? null)),
  http.get(api('/venues/:id/stats'), () => envelope(f.venueStats)),
  http.get(api('/venues/:id/seasons'), () => envelope(f.venueSeasons)),
  http.get(api('/venues/:id/score-distribution'), () => envelope(f.scoreBands)),
  http.get(api('/head-to-head'), () => envelope(f.headToHead)),
  http.get(api('/matchups'), () => envelope(f.matchup)),
  http.get(api('/leaderboards/batting'), () => envelope(f.battingLeaders)),
  http.get(api('/leaderboards/bowling'), () => envelope(f.bowlingLeaders)),
];

export const server = setupServer(...handlers);

/** URLs requested from the API during the current test (path and query, without the origin). */
export const requests: string[] = [];
server.events.on('request:start', ({ request }) => {
  const url = new URL(request.url);
  requests.push(url.pathname + url.search);
});

/** The requests to an API path (e.g. "/players"), as URL objects for asserting parameters. */
export function requestsTo(path: string): URL[] {
  return requests.map((r) => new URL(r, 'http://test')).filter((u) => u.pathname === `/api/v1${path}`);
}
