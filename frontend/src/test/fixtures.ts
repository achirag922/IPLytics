import type { Schemas } from '@/api/client';

/**
 * Typed API fixtures for component and page tests. They follow the generated OpenAPI types, so a backend contract
 * change breaks these tests at compile time. Values are small and hand-made, not real data.
 */
type S = Schemas;

export const MI: S['TeamRef'] = { id: 4, code: 'MI', name: 'Mumbai Indians' };
export const CSK: S['TeamRef'] = { id: 2, code: 'CSK', name: 'Chennai Super Kings' };
export const ROHIT: S['PlayerRef'] = { id: 346, name: 'RG Sharma' };
export const BUMRAH: S['PlayerRef'] = { id: 210, name: 'JJ Bumrah' };
export const DHONI: S['PlayerRef'] = { id: 221, name: 'MS Dhoni' };
export const WANKHEDE: S['VenueRef'] = { id: 6, name: 'Wankhede Stadium', city: 'Mumbai' };

export const overview: S['OverviewDto'] = {
  seasons: 19,
  firstSeason: 2008,
  lastSeason: 2026,
  matches: 1243,
  teams: 15,
  players: 809,
  venues: 59,
  deliveries: 295732,
};

export const seasons: S['SeasonDto'][] = [
  {
    year: 2025,
    name: 'IPL 2025',
    sourceSeason: '2025',
    matches: 70,
    champion: CSK,
    runs: 24000,
    wickets: 850,
    fours: 2100,
    sixes: 1200,
    averageScore: 175.5,
    runRate: 9.1,
  },
  {
    year: 2026,
    name: 'IPL 2026',
    sourceSeason: '2026',
    matches: 74,
    champion: MI,
    runs: 26000,
    wickets: 880,
    fours: 2200,
    sixes: 1300,
    averageScore: 182.4,
    runRate: 9.5,
  },
];

export const record = (played: number, won: number, lost: number): S['TeamRecordDto'] => ({
  played,
  won,
  lost,
  tied: 0,
  noResult: played - won - lost,
  winPercentage: played ? Math.round((won / played) * 10000) / 100 : undefined,
});

export const teams: S['TeamDto'][] = [
  {
    id: 4,
    code: 'MI',
    name: 'Mumbai Indians',
    active: true,
    homeCity: 'Mumbai',
    firstSeason: 2008,
    lastSeason: 2026,
    matches: 280,
    names: [],
    record: record(280, 157, 119),
    latestSeason: { season: 2026, record: record(14, 8, 6) },
  },
  {
    id: 2,
    code: 'CSK',
    name: 'Chennai Super Kings',
    active: true,
    homeCity: 'Chennai',
    firstSeason: 2008,
    lastSeason: 2026,
    matches: 260,
    names: [],
    record: record(260, 150, 106),
    latestSeason: { season: 2026, record: record(14, 6, 8) },
  },
  {
    id: 3,
    code: 'DEC',
    name: 'Deccan Chargers',
    active: false,
    homeCity: 'Hyderabad',
    firstSeason: 2008,
    lastSeason: 2012,
    matches: 75,
    names: [],
    record: record(75, 29, 46),
    latestSeason: { season: 2012, record: record(16, 4, 11) },
  },
];

export const teamStats: S['TeamStatsDto'] = {
  overall: record(14, 8, 6),
  battingFirst: record(7, 5, 2),
  chasing: record(7, 3, 4),
  home: record(7, 5, 2),
  away: record(7, 3, 4),
  runsFor: 2450,
  inningsBatted: 14,
  averageScore: 175,
  runsAgainst: 2380,
  inningsBowled: 14,
  averageConceded: 170,
  runRate: 9.12,
  opponentRunRate: 8.95,
  highestScore: 246,
};

export const teamSeasons: S['TeamSeasonStatsDto'][] = [
  { season: 2025, stats: { ...teamStats, highestScore: 220 } },
  { season: 2026, stats: teamStats },
];

export const battingSummary: S['BattingSummaryDto'] = {
  matches: 12,
  innings: 12,
  notOuts: 1,
  runs: 420,
  balls: 300,
  average: 38.18,
  strikeRate: 140,
  fours: 40,
  sixes: 18,
  fifties: 3,
  hundreds: 1,
  ducks: 0,
  highestScore: 105,
  highestScoreNotOut: true,
  dotBalls: 90,
  dotBallPercentage: 30,
  boundaryPercentage: 19.33,
  ballsPerBoundary: 5.17,
};

export const bowlingSummary: S['BowlingSummaryDto'] = {
  matches: 12,
  innings: 12,
  legalBalls: 288,
  overs: '48.0',
  runsConceded: 336,
  wickets: 18,
  economy: 7,
  average: 18.67,
  strikeRate: 16,
  dotBalls: 130,
  dotBallPercentage: 45.14,
  wides: 5,
  noBalls: 1,
  foursConceded: 25,
  sixesConceded: 10,
  threeWicketHauls: 2,
  fourWicketHauls: 1,
  fiveWicketHauls: 0,
  bestFigures: '4/18',
};

export const battingLeaders: S['BattingLeaderDto'][] = [
  {
    rank: 1,
    player: ROHIT,
    innings: 14,
    runs: 610,
    balls: 400,
    dismissals: 13,
    average: 46.92,
    strikeRate: 152.5,
    fours: 60,
    sixes: 30,
    fifties: 5,
    hundreds: 1,
    value: 610,
  },
  {
    rank: 2,
    player: DHONI,
    innings: 14,
    runs: 400,
    balls: 260,
    dismissals: 8,
    average: 50,
    strikeRate: 153.85,
    fours: 25,
    sixes: 28,
    fifties: 2,
    hundreds: 0,
    value: 400,
  },
];

export const bowlingLeaders: S['BowlingLeaderDto'][] = [
  {
    rank: 1,
    player: BUMRAH,
    innings: 14,
    legalBalls: 336,
    overs: '56.0',
    runsConceded: 360,
    wickets: 24,
    economy: 6.43,
    average: 15,
    strikeRate: 14,
    dotBalls: 160,
    value: 24,
  },
];

export const seasonSummary: S['SeasonSummaryDto'] = {
  year: 2026,
  name: 'IPL 2026',
  matches: 74,
  totalRuns: 26000,
  wickets: 880,
  fours: 2200,
  sixes: 1300,
  averageScore: 182.4,
  runRate: 9.5,
  highestTeamScore: {
    team: MI,
    opponent: CSK,
    runs: 265,
    wickets: 4,
    overs: '20.0',
    matchId: 9001,
    date: '2026-04-10',
  },
  lowestTeamScore: {
    team: CSK,
    opponent: MI,
    runs: 101,
    wickets: 10,
    overs: '15.2',
    matchId: 9002,
    date: '2026-04-20',
  },
  highestIndividualScore: {
    player: ROHIT,
    team: MI,
    opponent: CSK,
    runs: 152,
    balls: 67,
    notOut: true,
    matchId: 9001,
    date: '2026-04-10',
  },
  champion: MI,
  topRunScorer: { player: ROHIT, value: 776 },
  topWicketTaker: { player: BUMRAH, value: 29 },
  highestStrikeRate: { player: DHONI, value: 237.31 },
  strikeRateMinimumBalls: 100,
  bestEconomy: { player: BUMRAH, value: 6.65 },
  economyMinimumOvers: 10,
};

export const pointsTable: S['PointsTableDto'] = {
  year: 2026,
  rows: [
    {
      position: 1,
      team: MI,
      matches: 14,
      wins: 9,
      losses: 5,
      ties: 0,
      noResults: 0,
      points: 18,
      netRunRate: 0.412,
      runsFor: 2500,
      oversFaced: '270.0',
      runsAgainst: 2400,
      oversBowled: '275.0',
    },
    {
      position: 2,
      team: CSK,
      matches: 14,
      wins: 5,
      losses: 9,
      ties: 0,
      noResults: 0,
      points: 10,
      netRunRate: -0.412,
      runsFor: 2400,
      oversFaced: '275.0',
      runsAgainst: 2500,
      oversBowled: '270.0',
    },
  ],
};

export const playerList: S['PlayerListItemDto'][] = [
  {
    id: 346,
    name: 'RG Sharma',
    sourceId: '740742ef',
    team: MI,
    matches: 270,
    firstSeason: 2008,
    lastSeason: 2026,
    battingInnings: 265,
    runs: 7329,
    battingAverage: 29.5,
    strikeRate: 132.1,
    bowlingInnings: 30,
    wickets: 15,
    economy: 8.1,
  },
  {
    id: 210,
    name: 'JJ Bumrah',
    sourceId: '462411b3',
    team: MI,
    matches: 150,
    firstSeason: 2013,
    lastSeason: 2026,
    battingInnings: 40,
    runs: 90,
    battingAverage: 7.5,
    strikeRate: 90,
    bowlingInnings: 150,
    wickets: 187,
    economy: 7.2,
  },
];

export const playerDetail: S['PlayerDetailDto'] = {
  id: 346,
  name: 'RG Sharma',
  sourceId: '740742ef',
  matches: 270,
  firstSeason: 2008,
  lastSeason: 2026,
  aliases: [{ name: 'RG Sharma', firstSeason: 2008, lastSeason: 2026 }],
  teams: [
    {
      team: { id: 3, code: 'DEC', name: 'Deccan Chargers' },
      matches: 45,
      firstSeason: 2008,
      lastSeason: 2010,
    },
    { team: MI, matches: 225, firstSeason: 2011, lastSeason: 2026 },
  ],
};

export const playerBatting: S['PlayerBattingDto'] = {
  player: ROHIT,
  summary: battingSummary,
  bySeason: [
    { season: 2025, stats: { ...battingSummary, runs: 300 } },
    { season: 2026, stats: battingSummary },
  ],
};

export const playerBowling: S['PlayerBowlingDto'] = {
  player: ROHIT,
  summary: bowlingSummary,
  bySeason: [{ season: 2026, stats: bowlingSummary }],
};

export const playerPhases: S['PlayerPhasesDto'] = {
  player: ROHIT,
  batting: [
    {
      phase: 'powerplay',
      runs: 200,
      balls: 150,
      strikeRate: 133.33,
      runShare: 47.62,
      fours: 25,
      sixes: 6,
      boundaryPercentage: 20.67,
    },
    {
      phase: 'middle',
      runs: 150,
      balls: 110,
      strikeRate: 136.36,
      runShare: 35.71,
      fours: 10,
      sixes: 6,
      boundaryPercentage: 14.55,
    },
    {
      phase: 'death',
      runs: 70,
      balls: 40,
      strikeRate: 175,
      runShare: 16.67,
      fours: 5,
      sixes: 6,
      boundaryPercentage: 27.5,
    },
  ],
  bowling: [
    { phase: 'powerplay', legalBalls: 0, overs: '0.0', runsConceded: 0, wickets: 0 },
    { phase: 'middle', legalBalls: 0, overs: '0.0', runsConceded: 0, wickets: 0 },
    { phase: 'death', legalBalls: 0, overs: '0.0', runsConceded: 0, wickets: 0 },
  ],
};

export const playerVsTeams: S['PlayerVsTeamsDto'] = {
  player: ROHIT,
  opponents: [{ opponent: CSK, matches: 30, batting: battingSummary }],
};

export const playerVenues: S['PlayerVenuesDto'] = {
  player: ROHIT,
  venues: [{ venue: WANKHEDE, matches: 80, batting: battingSummary }],
};

export const matchSummary = (
  id: number,
  overrides: Partial<S['MatchSummaryDto']> = {},
): S['MatchSummaryDto'] => ({
  id,
  sourceMatchId: 100000 + id,
  season: 2019,
  date: '2019-04-10',
  endDate: '2019-04-10',
  stage: 'League',
  matchNumber: 15,
  venue: WANKHEDE,
  team1: { team: MI, nameAtTheTime: 'Mumbai Indians' },
  team2: { team: CSK, nameAtTheTime: 'Chennai Super Kings' },
  winner: MI,
  result: 'runs',
  resultMargin: 10,
  superOver: false,
  resultText: 'Mumbai Indians won by 10 runs',
  innings: [
    { inningsNumber: 1, team: MI, runs: 170, wickets: 5, overs: '20.0', superOver: false },
    { inningsNumber: 2, team: CSK, runs: 160, wickets: 8, overs: '20.0', superOver: false },
  ],
  ...overrides,
});

export const matchDetail: S['MatchDetailDto'] = {
  ...matchSummary(77),
  city: 'Mumbai',
  toss: { winner: CSK, decision: 'field' },
  playerOfMatch: ROHIT,
  target: { runs: 171, overs: 20 },
  scheduledOvers: 20,
  officials: { umpires: ['Umpire A', 'Umpire B'], tvUmpires: [], matchReferees: [], reserveUmpires: [] },
};

export const scorecard: S['ScorecardDto'] = {
  match: matchDetail,
  innings: [
    {
      inningsNumber: 1,
      superOver: false,
      battingTeam: MI,
      bowlingTeam: CSK,
      runs: 170,
      wickets: 5,
      overs: '20.0',
      legalBalls: 120,
      runRate: 8.5,
      extras: { total: 10, wides: 4, noBalls: 1, byes: 2, legByes: 3, penalty: 0 },
      batting: [
        {
          position: 1,
          player: ROHIT,
          dismissal: 'c MS Dhoni b DL Chahar',
          dismissalKind: 'caught',
          runs: 60,
          balls: 40,
          fours: 6,
          sixes: 3,
          strikeRate: 150,
          out: true,
        },
        {
          position: 2,
          player: BUMRAH,
          dismissal: 'not out',
          runs: 5,
          balls: 3,
          fours: 1,
          sixes: 0,
          strikeRate: 166.67,
          out: false,
        },
      ],
      didNotBat: [{ id: 999, name: 'Tail Ender' }],
      bowling: [
        {
          player: DHONI,
          overs: '4.0',
          maidens: 1,
          legalBalls: 24,
          runsConceded: 22,
          wickets: 2,
          economy: 5.5,
          dotBalls: 12,
          wides: 1,
          noBalls: 0,
          foursConceded: 2,
          sixesConceded: 1,
        },
      ],
      fallOfWickets: [{ wicket: 1, score: 45, over: '5.3', player: ROHIT }],
    },
  ],
};

export const progression: S['MatchProgressionDto'] = {
  matchId: 77,
  innings: [
    {
      inningsNumber: 1,
      superOver: false,
      team: MI,
      overs: [
        {
          over: 1,
          runs: 8,
          wickets: 0,
          extras: 1,
          legalBalls: 6,
          cumulativeRuns: 8,
          cumulativeWickets: 0,
          runRate: 8,
        },
        {
          over: 2,
          runs: 12,
          wickets: 1,
          extras: 0,
          legalBalls: 6,
          cumulativeRuns: 20,
          cumulativeWickets: 1,
          runRate: 10,
        },
      ],
    },
    {
      inningsNumber: 2,
      superOver: false,
      team: CSK,
      target: 171,
      overs: [
        {
          over: 1,
          runs: 6,
          wickets: 1,
          extras: 0,
          legalBalls: 6,
          cumulativeRuns: 6,
          cumulativeWickets: 1,
          runRate: 6,
        },
      ],
    },
  ],
};

export const venues: S['VenueDto'][] = [
  {
    id: 6,
    name: 'Wankhede Stadium',
    city: 'Mumbai',
    curated: true,
    matches: 120,
    firstSeason: 2008,
    lastSeason: 2026,
    aliases: ['Wankhede Stadium, Mumbai'],
  },
  {
    id: 19,
    name: 'MA Chidambaram Stadium',
    city: 'Chennai',
    curated: true,
    matches: 90,
    firstSeason: 2008,
    lastSeason: 2026,
    aliases: ['Chepauk'],
  },
];

export const venueStats: S['VenueStatsDto'] = {
  matches: 120,
  decisiveMatches: 116,
  battingFirstWins: 52,
  chasingWins: 64,
  battingFirstWinPercentage: 44.83,
  chasingWinPercentage: 55.17,
  averageFirstInningsScore: 172.4,
  highestScore: 246,
  lowestScore: 67,
  averageRunRate: 8.9,
  innings: 238,
  wicketsPerInnings: 6.1,
  boundariesPerInnings: 21.4,
};

export const scoreBands: S['ScoreBandDto'][] = [
  { to: 119, innings: 5 },
  { from: 120, to: 139, innings: 10 },
  { from: 140, to: 159, innings: 25 },
  { from: 160, to: 179, innings: 30 },
  { from: 180, to: 199, innings: 25 },
  { from: 200, to: 219, innings: 15 },
  { from: 220, innings: 6 },
];

export const venueSeasons: S['VenueSeasonStatsDto'][] = [
  { season: 2025, stats: venueStats },
  { season: 2026, stats: { ...venueStats, highestScore: 250 } },
];

export const headToHead: S['HeadToHeadDto'] = {
  matches: 41,
  ties: 0,
  noResults: 0,
  team1: { team: CSK, wins: 20, winsBattingFirst: 8, winsChasing: 12, averageScore: 157.49 },
  team2: { team: MI, wins: 21, winsBattingFirst: 10, winsChasing: 11, averageScore: 156.88 },
  bySeason: [
    { season: 2025, matches: 2, team1Wins: 1, team2Wins: 1, ties: 0, noResults: 0 },
    { season: 2026, matches: 2, team1Wins: 2, team2Wins: 0, ties: 0, noResults: 0 },
  ],
  recentMatches: [matchSummary(501, { resultText: 'Chennai Super Kings won by 8 wickets', winner: CSK })],
};

export const comparison = (discipline: 'batting' | 'bowling'): S['ComparisonDto'] => ({
  discipline,
  players: [
    {
      player: ROHIT,
      ...(discipline === 'batting'
        ? { batting: battingSummary }
        : { bowling: { ...bowlingSummary, wickets: 15 } }),
    },
    {
      player: BUMRAH,
      ...(discipline === 'batting'
        ? { batting: { ...battingSummary, runs: 90 } }
        : { bowling: bowlingSummary }),
    },
  ],
});

export const matchup: S['MatchupDto'] = {
  batter: ROHIT,
  bowler: BUMRAH,
  occurred: true,
  innings: 8,
  balls: 50,
  runs: 70,
  dismissals: 2,
  fours: 8,
  sixes: 3,
  dotBalls: 18,
  strikeRate: 140,
  average: 35,
  dotBallPercentage: 36,
  boundaryPercentage: 22,
};
