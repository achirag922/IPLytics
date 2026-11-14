import { CalendarRange, CircleDot, Shield, Trophy, Users } from 'lucide-react';
import { useMemo } from 'react';
import { Link } from 'react-router';
import type { Season, SeasonSummary } from '@/api/seasonsApi';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { PlayerLink, TeamLink } from '@/components/cards/EntityLinks';
import { BarChart, ChartCard, LineChart } from '@/components/charts';
import { FilterBar, SeasonFilter } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { ChartSkeleton, StatCardSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import {
  useBattingLeaderboardQuery,
  useBowlingLeaderboardQuery,
  useOverviewQuery,
} from '@/hooks/useAnalyticsQueries';
import { useSeasonsQuery, useSeasonSummaryQuery } from '@/hooks/useSeasonQueries';
import { useUrlState } from '@/hooks/useUrlState';
import { formatDecimal, formatInteger, formatScore } from '@/lib/format';

/** Season summary qualification shown on the highlight cards (defaults of the season summary API). */
const STRIKE_RATE_MINIMUM_BALLS = 100;
const ECONOMY_MINIMUM_OVERS = 10;

export function DashboardPage() {
  const url = useUrlState();
  const seasons = useSeasonsQuery();
  const latest = useMemo(
    () => Math.max(0, ...(seasons.data ?? []).map((s) => s.year)) || undefined,
    [seasons.data],
  );
  const season = url.getNumber('season') ?? latest;

  return (
    <>
      <PageHeader
        title="IPL at a glance"
        description="Every Indian Premier League season, from ball-by-ball data."
      />
      <OverviewKpis />

      <FilterBar label="Season" className="mt-6">
        <SeasonFilter value={season} allowAll={false} onChange={(s) => url.update({ season: s })} />
        {season !== undefined && (
          <Link
            to={`/seasons/${season}`}
            className="self-center text-sm font-medium text-primary hover:underline"
          >
            Full {season} season →
          </Link>
        )}
      </FilterBar>

      <SeasonHighlights season={season} />
      <SeasonTrends seasons={seasons} />
      <TopPlayers season={season} />
    </>
  );
}

function OverviewKpis() {
  const overview = useOverviewQuery();
  return (
    <QueryContent query={overview} loading={<StatCardSkeleton count={5} className="lg:grid-cols-5" />}>
      {(o) => (
        <StatGrid className="lg:grid-cols-5">
          <StatCard
            label="Seasons"
            value={formatInteger(o.seasons)}
            hint={`${o.firstSeason}–${o.lastSeason}`}
            icon={CalendarRange}
          />
          <StatCard label="Matches" value={formatInteger(o.matches)} icon={Trophy} />
          <StatCard label="Teams" value={formatInteger(o.teams)} hint="Franchises" icon={Shield} />
          <StatCard label="Players" value={formatInteger(o.players)} hint="In a playing XI" icon={Users} />
          <StatCard label="Deliveries" value={formatInteger(o.deliveries)} icon={CircleDot} />
        </StatGrid>
      )}
    </QueryContent>
  );
}

function SeasonHighlights({ season }: { season: number | undefined }) {
  const summary = useSeasonSummaryQuery(season, {
    minimumBalls: STRIKE_RATE_MINIMUM_BALLS,
    minimumOvers: ECONOMY_MINIMUM_OVERS,
  });
  return (
    <section aria-labelledby="highlights-heading" className="space-y-3">
      <h2 id="highlights-heading" className="text-lg font-semibold">
        {season ?? ''} season highlights
      </h2>
      <QueryContent query={summary} loading={<StatCardSkeleton count={6} className="lg:grid-cols-3" />}>
        {(s) => <HighlightCards summary={s} />}
      </QueryContent>
    </section>
  );
}

function HighlightCards({ summary: s }: { summary: SeasonSummary }) {
  const top = s.highestTeamScore;
  const individual = s.highestIndividualScore;
  return (
    <StatGrid className="lg:grid-cols-3">
      <StatCard
        label="Highest team score"
        value={top ? `${top.runs}/${top.wickets}` : '–'}
        hint={
          top && (
            <>
              <TeamLink team={top.team} /> v {top.opponent?.code}
            </>
          )
        }
      />
      <StatCard
        label="Highest individual score"
        value={individual ? `${formatScore(individual.runs, individual.notOut)} (${individual.balls})` : '–'}
        hint={
          individual && (
            <>
              <PlayerLink player={individual.player} /> v {individual.opponent?.code}
            </>
          )
        }
      />
      <StatCard
        label="Most runs"
        value={formatInteger(s.topRunScorer?.value)}
        hint={<PlayerLink player={s.topRunScorer?.player} />}
      />
      <StatCard
        label="Most wickets"
        value={formatInteger(s.topWicketTaker?.value)}
        hint={<PlayerLink player={s.topWicketTaker?.player} />}
      />
      <StatCard
        label="Highest strike rate"
        value={formatDecimal(s.highestStrikeRate?.value)}
        hint={
          <>
            <PlayerLink player={s.highestStrikeRate?.player} /> · min {s.strikeRateMinimumBalls} balls
          </>
        }
      />
      <StatCard
        label="Best economy"
        value={formatDecimal(s.bestEconomy?.value)}
        hint={
          <>
            <PlayerLink player={s.bestEconomy?.player} /> · min {s.economyMinimumOvers} overs
          </>
        }
      />
    </StatGrid>
  );
}

function SeasonTrends({ seasons }: { seasons: ReturnType<typeof useSeasonsQuery> }) {
  return (
    <section aria-label="Trends by season" className="mt-6 grid gap-4 lg:grid-cols-3">
      <TrendCard seasons={seasons} title="Runs by season" pick={(s) => s.runs} label="Runs" kind="bar" />
      <TrendCard
        seasons={seasons}
        title="Matches by season"
        pick={(s) => s.matches}
        label="Matches"
        kind="bar"
      />
      <TrendCard
        seasons={seasons}
        title="Average score by season"
        pick={(s) => s.averageScore}
        label="Average score"
        kind="line"
      />
    </section>
  );
}

interface TrendCardProps {
  seasons: ReturnType<typeof useSeasonsQuery>;
  title: string;
  label: string;
  pick: (season: Season) => number | undefined;
  kind: 'bar' | 'line';
}

function TrendCard({ seasons, title, label, pick, kind }: TrendCardProps) {
  const Chart = kind === 'bar' ? BarChart : LineChart;
  return (
    <ChartCard title={title}>
      <QueryContent query={seasons} loading={<ChartSkeleton height={240} />} isEmpty={(d) => d.length === 0}>
        {(data) => (
          <Chart
            height={240}
            categories={data.map((s) => s.year)}
            series={[{ name: label, data: data.map(pick) }]}
            ariaLabel={`${title}, ${data[0]?.year} to ${data[data.length - 1]?.year}`}
          />
        )}
      </QueryContent>
    </ChartCard>
  );
}

function TopPlayers({ season }: { season: number | undefined }) {
  const params = season === undefined ? undefined : { season, limit: 10 };
  const batting = useBattingLeaderboardQuery(params && { ...params, metric: 'runs' });
  const bowling = useBowlingLeaderboardQuery(params && { ...params, metric: 'wickets' });
  return (
    <section aria-label="Top players" className="mt-4 grid gap-4 lg:grid-cols-2">
      <ChartCard title={`Top 10 run scorers, ${season ?? ''}`}>
        <QueryContent
          query={batting}
          loading={<ChartSkeleton height={320} />}
          isEmpty={(d) => d.length === 0}
        >
          {(rows) => (
            <BarChart
              horizontal
              height={320}
              categories={rows.map((r) => r.player?.name ?? '')}
              series={[{ name: 'Runs', data: rows.map((r) => r.runs) }]}
              ariaLabel={`Top 10 run scorers in ${season}`}
            />
          )}
        </QueryContent>
      </ChartCard>
      <ChartCard title={`Top 10 wicket takers, ${season ?? ''}`}>
        <QueryContent
          query={bowling}
          loading={<ChartSkeleton height={320} />}
          isEmpty={(d) => d.length === 0}
        >
          {(rows) => (
            <BarChart
              horizontal
              height={320}
              categories={rows.map((r) => r.player?.name ?? '')}
              series={[{ name: 'Wickets', data: rows.map((r) => r.wickets), color: '#059669' }]}
              ariaLabel={`Top 10 wicket takers in ${season}`}
            />
          )}
        </QueryContent>
      </ChartCard>
    </section>
  );
}
