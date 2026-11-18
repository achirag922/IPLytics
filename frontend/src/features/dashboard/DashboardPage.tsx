import { CalendarRange, CircleDot, Flame, Shield, Star, Trophy, Users } from 'lucide-react';
import { MatchCard, PlayerCard } from '@/components/cards/EntityCards';
import { EntityHero } from '@/components/layout/EntityHero';
import { TeamLogo } from '@/components/media/TeamLogo';
import { teamBranding } from '@/config/media/teamBranding';
import { useMatchesQuery } from '@/hooks/useMatchQueries';
import { useMemo } from 'react';
import { Link, useNavigate } from 'react-router';
import type { Season, SeasonSummary } from '@/api/seasonsApi';
import { StatCard, StatGrid, type Trend } from '@/components/cards/StatCard';
import { PlayerLink, TeamLink } from '@/components/cards/EntityLinks';
import { BarChart, ChartCard, LineChart } from '@/components/charts';
import { SeasonFilter } from '@/components/filters';
import { CardGridSkeleton, ChartSkeleton, StatCardSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import {
  useBattingLeaderboardQuery,
  useBowlingLeaderboardQuery,
  useOverviewQuery,
} from '@/hooks/useAnalyticsQueries';
import { useSeasonsQuery, useSeasonSummaryQuery } from '@/hooks/useSeasonQueries';
import { useUrlState } from '@/hooks/useUrlState';
import { formatDecimal, formatInteger, formatPercent, formatScore } from '@/lib/format';
import { useSeasonComparisonQuery } from '@/hooks/useInsightQueries';

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
      <EntityHero
        eyebrow="Indian Premier League · ball-by-ball analytics"
        title={
          <>
            IPL <span className="text-brand-gradient">at a glance</span>
          </>
        }
        subtitle="Every season, team, player and match, from the first ball of 2008 to the latest final."
        actions={
          <form
            role="search"
            aria-label="Season"
            onSubmit={(event) => event.preventDefault()}
            className="flex flex-wrap items-end gap-3"
          >
            <SeasonFilter value={season} allowAll={false} onChange={(s) => url.update({ season: s })} />
            {season !== undefined && (
              <Link
                to={`/seasons/${season}`}
                className="inline-flex h-9 items-center rounded-lg bg-brand-gradient px-3.5 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
              >
                Full {season} season →
              </Link>
            )}
          </form>
        }
      />
      <OverviewKpis />

      <SeasonHighlights season={season} />
      <SeasonAtAGlance season={season} />
      <LatestResults season={season} />
      <SeasonTrends seasons={seasons} onSelect={(s) => url.update({ season: s })} />
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
    <section aria-labelledby="highlights-heading" className="mt-8 space-y-3">
      <h2 id="highlights-heading" className="text-lg font-semibold tracking-tight">
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
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-3">
        <ChampionCard season={s.year} champion={s.champion} />
        <StatCard
          label="Highest team score"
          icon={Flame}
          value={top ? `${top.runs}/${top.wickets}` : '–'}
          hint={
            top && (
              <span className="flex items-center gap-1.5">
                <TeamLogo team={top.team} size="xs" />
                <TeamLink team={top.team} /> v {top.opponent?.code}
              </span>
            )
          }
        />
        <StatCard
          label="Highest individual score"
          icon={Star}
          value={
            individual ? `${formatScore(individual.runs, individual.notOut)} (${individual.balls})` : '–'
          }
          hint={
            individual && (
              <>
                <PlayerLink player={individual.player} /> v {individual.opponent?.code}
              </>
            )
          }
        />
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <PlayerCard
          label="Most runs"
          player={s.topRunScorer?.player}
          value={formatInteger(s.topRunScorer?.value)}
          hint="Orange cap"
        />
        <PlayerCard
          label="Most wickets"
          player={s.topWicketTaker?.player}
          value={formatInteger(s.topWicketTaker?.value)}
          hint="Purple cap"
        />
        <PlayerCard
          label="Highest strike rate"
          player={s.highestStrikeRate?.player}
          value={formatDecimal(s.highestStrikeRate?.value)}
          hint={`min ${s.strikeRateMinimumBalls} balls`}
        />
        <PlayerCard
          label="Best economy"
          player={s.bestEconomy?.player}
          value={formatDecimal(s.bestEconomy?.value)}
          hint={`min ${s.economyMinimumOvers} overs`}
        />
      </div>
    </div>
  );
}

/** The season's champion (winner of the Final), with the franchise crest. */
function ChampionCard({ season, champion }: { season: number; champion: SeasonSummary['champion'] }) {
  const brand = teamBranding(champion?.code, champion?.name);
  const body = (
    <>
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-90"
        style={{
          background: `radial-gradient(120% 140% at 100% 0%, ${brand.primary}33, transparent 60%)`,
        }}
      />
      <div className="relative flex items-center gap-4">
        <TeamLogo team={champion} size="xl" />
        <div className="min-w-0">
          <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            <Trophy aria-hidden="true" className="size-3.5 text-amber-500" />
            {season} champions
          </p>
          <p className="mt-1 text-xl font-bold leading-tight tracking-tight">
            {champion?.name ?? 'Not decided'}
          </p>
        </div>
      </div>
    </>
  );
  const className = 'card-interactive relative overflow-hidden rounded-xl border bg-card p-4 shadow-sm';
  return champion ? (
    <Link to={`/teams/${champion.id}`} className={className}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}

/** The newest results of the season as match cards. */
function LatestResults({ season }: { season: number | undefined }) {
  const matches = useMatchesQuery(season === undefined ? undefined : { season, page: 0, size: 3 });
  return (
    <section aria-labelledby="latest-heading" className="mt-8 space-y-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="latest-heading" className="text-lg font-semibold tracking-tight">
          Latest results{season !== undefined && `, ${season}`}
        </h2>
        <Link
          to={`/matches?season=${season ?? ''}`}
          className="text-sm font-medium text-primary hover:underline"
        >
          All matches →
        </Link>
      </div>
      <QueryContent
        query={matches}
        loading={<CardGridSkeleton count={3} label="Loading matches" />}
        isEmpty={(d) => d.items.length === 0}
        emptyTitle="No matches in this season"
      >
        {(d) => (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {d.items.map((m) => (
              <MatchCard key={m.id} match={m} />
            ))}
          </div>
        )}
      </QueryContent>
    </section>
  );
}

/** Headline season metrics with the backend's change from the previous season and a sparkline of every season. */
function SeasonAtAGlance({ season }: { season: number | undefined }) {
  const trend = useSeasonComparisonQuery(useMemo(() => ({}), []));
  const rows = trend.data?.seasons ?? [];
  const index = rows.findIndex((r) => r.season === season);
  const previous = index > 0 ? rows[index - 1]?.season : undefined;
  const change = useSeasonComparisonQuery(
    useMemo(
      () => (season !== undefined && previous !== undefined ? { seasons: [previous, season] } : undefined),
      [season, previous],
    ),
  );
  const current = rows[index];
  const c = change.data?.change;
  const trendOf = (delta: number | undefined, unit = ''): Trend | undefined =>
    delta === undefined || previous === undefined
      ? undefined
      : {
          direction: delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat',
          label: `${delta > 0 ? '+' : ''}${formatDecimal(delta)}${unit} vs ${previous}`,
        };
  return (
    <section aria-labelledby="glance-heading" className="mt-8 space-y-3">
      <h2 id="glance-heading" className="text-lg font-semibold tracking-tight">
        {season ?? ''} at a glance
      </h2>
      {trend.isPending ? (
        <StatCardSkeleton />
      ) : (
        <StatGrid>
          <StatCard
            label="Average score"
            value={formatDecimal(current?.averageScore)}
            trend={trendOf(c?.averageScore)}
            sparkline={rows.map((r) => r.averageScore)}
          />
          <StatCard
            label="Run rate"
            value={formatDecimal(current?.runRate)}
            trend={trendOf(c?.runRate)}
            sparkline={rows.map((r) => r.runRate)}
          />
          <StatCard
            label="Sixes per match"
            value={formatDecimal(current?.sixesPerMatch)}
            trend={trendOf(c?.sixesPerMatch)}
            sparkline={rows.map((r) => r.sixesPerMatch)}
          />
          <StatCard
            label="Batting first won"
            value={formatPercent(current?.battingFirstWinPercentage)}
            trend={trendOf(c?.battingFirstWinPercentage, ' pts')}
            sparkline={rows.map((r) => r.battingFirstWinPercentage)}
            hint={
              <Link to="/insights/chasing" className="text-primary hover:underline">
                Batting first vs chasing →
              </Link>
            }
          />
        </StatGrid>
      )}
    </section>
  );
}

function SeasonTrends({
  seasons,
  onSelect,
}: {
  seasons: ReturnType<typeof useSeasonsQuery>;
  onSelect: (season: number) => void;
}) {
  return (
    <section aria-label="Trends by season" className="mt-8 grid gap-4 lg:grid-cols-3">
      <TrendCard
        seasons={seasons}
        title="Runs by season"
        pick={(s) => s.runs}
        label="Runs"
        kind="bar"
        onSelect={onSelect}
      />
      <TrendCard
        seasons={seasons}
        title="Matches by season"
        pick={(s) => s.matches}
        label="Matches"
        kind="bar"
        onSelect={onSelect}
      />
      <TrendCard
        seasons={seasons}
        title="Average score by season"
        pick={(s) => s.averageScore}
        label="Average score"
        kind="line"
        onSelect={onSelect}
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
  onSelect: (season: number) => void;
}

function TrendCard({ seasons, title, label, pick, kind, onSelect }: TrendCardProps) {
  const Chart = kind === 'bar' ? BarChart : LineChart;
  return (
    <ChartCard title={title} description="Click a season to focus the dashboard on it">
      <QueryContent query={seasons} loading={<ChartSkeleton height={240} />} isEmpty={(d) => d.length === 0}>
        {(data) => (
          <Chart
            height={240}
            categories={data.map((s) => s.year)}
            series={[{ name: label, data: data.map(pick) }]}
            ariaLabel={`${title}, ${data[0]?.year} to ${data[data.length - 1]?.year}`}
            onItemClick={(_, year) => onSelect(Number(year))}
          />
        )}
      </QueryContent>
    </ChartCard>
  );
}

function TopPlayers({ season }: { season: number | undefined }) {
  const navigate = useNavigate();
  const params = season === undefined ? undefined : { season, limit: 10 };
  const batting = useBattingLeaderboardQuery(params && { ...params, metric: 'runs' });
  const bowling = useBowlingLeaderboardQuery(params && { ...params, metric: 'wickets' });
  const open = (id: number | undefined) => id !== undefined && navigate(`/players/${id}`);
  return (
    <section aria-label="Top players" className="mt-4 grid gap-4 lg:grid-cols-2">
      <ChartCard title={`Top 10 run scorers, ${season ?? ''}`} description="Click a bar to open the player">
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
              onItemClick={(index) => open(rows[index]?.player?.id)}
            />
          )}
        </QueryContent>
      </ChartCard>
      <ChartCard title={`Top 10 wicket takers, ${season ?? ''}`} description="Click a bar to open the player">
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
              onItemClick={(index) => open(rows[index]?.player?.id)}
            />
          )}
        </QueryContent>
      </ChartCard>
      <p className="text-sm text-muted-foreground lg:col-span-2">
        Full rankings:{' '}
        <Link to={`/leaderboards?season=${season ?? ''}`} className="text-primary hover:underline">
          leaderboards
        </Link>
        {' · '}
        <Link to={`/seasons/${season ?? ''}`} className="text-primary hover:underline">
          season page
        </Link>
      </p>
    </section>
  );
}
