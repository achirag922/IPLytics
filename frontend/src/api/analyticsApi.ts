import { getData, getList } from './client/request';
import type { QueryOf, Schemas } from './client/types';

export type Overview = Schemas['OverviewDto'];
export type BattingLeader = Schemas['BattingLeaderDto'];
export type BowlingLeader = Schemas['BowlingLeaderDto'];
export type HeadToHead = Schemas['HeadToHeadDto'];
export type Comparison = Schemas['ComparisonDto'];
export type ComparedPlayer = Schemas['ComparedPlayerDto'];
export type Matchup = Schemas['MatchupDto'];
export type BattingLeaderboardQuery = QueryOf<'/api/v1/leaderboards/batting'>;
export type BowlingLeaderboardQuery = QueryOf<'/api/v1/leaderboards/bowling'>;
export type BattingMetric = NonNullable<BattingLeaderboardQuery['metric']>;
export type BowlingMetric = NonNullable<BowlingLeaderboardQuery['metric']>;
export type HeadToHeadQuery = QueryOf<'/api/v1/head-to-head'>;
export type ComparisonQuery = QueryOf<'/api/v1/players/compare'>;
export type Discipline = NonNullable<ComparisonQuery['discipline']>;
export type MatchupQuery = QueryOf<'/api/v1/matchups'>;
export type TossInsights = Schemas['TossInsightsDto'];
export type BattingFirstInsights = Schemas['BattingFirstInsightsDto'];
export type PhaseInsights = Schemas['PhaseInsightsDto'];
export type PhaseRow = Schemas['PhaseRowDto'];
export type SeasonComparison = Schemas['SeasonComparisonDto'];
export type SeasonInsight = Schemas['SeasonInsightDto'];
/** Insight scope: season range, team, opponent, venue (same for toss, batting first and phases). */
export type InsightQuery = QueryOf<'/api/v1/insights/toss'>;
export type SeasonComparisonQuery = QueryOf<'/api/v1/insights/seasons'>;

/** Cross-cutting analytics: data-set overview, leaderboards, rivalries, comparisons and matchups. */
export const analyticsApi = {
  overview: (signal?: AbortSignal) => getData<Overview>('/overview', undefined, signal),
  battingLeaderboard: (query: BattingLeaderboardQuery, signal?: AbortSignal) =>
    getList<BattingLeader>('/leaderboards/batting', query, signal),
  bowlingLeaderboard: (query: BowlingLeaderboardQuery, signal?: AbortSignal) =>
    getList<BowlingLeader>('/leaderboards/bowling', query, signal),
  headToHead: (query: HeadToHeadQuery, signal?: AbortSignal) =>
    getData<HeadToHead>('/head-to-head', query, signal),
  compare: (query: ComparisonQuery, signal?: AbortSignal) =>
    getData<Comparison>('/players/compare', query, signal),
  matchup: (query: MatchupQuery, signal?: AbortSignal) => getData<Matchup>('/matchups', query, signal),
  tossInsights: (query: InsightQuery, signal?: AbortSignal) =>
    getData<TossInsights>('/insights/toss', query, signal),
  battingFirstInsights: (query: InsightQuery, signal?: AbortSignal) =>
    getData<BattingFirstInsights>('/insights/batting-first', query, signal),
  phaseInsights: (query: InsightQuery, signal?: AbortSignal) =>
    getData<PhaseInsights>('/insights/phases', query, signal),
  seasonComparison: (query: SeasonComparisonQuery, signal?: AbortSignal) =>
    getData<SeasonComparison>('/insights/seasons', query, signal),
};
