import { Link } from 'react-router';
import type { BattingLeader, BowlingLeader } from '@/api/analyticsApi';
import type { TeamOpponentStats, TeamRecord, TeamSeasonStats, TeamVenueStats } from '@/api/teamsApi';
import { PlayerLink, TeamLink, VenueLink } from '@/components/cards/EntityLinks';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { BarChart, ChartCard, LineChart } from '@/components/charts';
import { ChartSkeleton, StatCardSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  useTeamOpponentsQuery,
  useTeamSeasonsQuery,
  useTeamStatsQuery,
  useTeamTopPerformersQuery,
  useTeamVenuesQuery,
} from '@/hooks/useTeamQueries';
import { formatDecimal, formatInteger, formatPercent } from '@/lib/format';
import { useTeamContext } from '@/features/detailContext';

const NO_STATS = 'No statistics available for the selected filters';

interface SituationRow {
  label: string;
  record?: TeamRecord;
}

/** Columns for a record (played / won / lost / ...) shared by several team tables. */
function recordColumns<T>(record: (row: T) => TeamRecord | undefined): Column<T>[] {
  return [
    {
      key: 'p',
      header: 'M',
      title: 'Matches',
      align: 'right',
      cell: (r) => formatInteger(record(r)?.played),
    },
    { key: 'w', header: 'W', title: 'Wins', align: 'right', cell: (r) => formatInteger(record(r)?.won) },
    { key: 'l', header: 'L', title: 'Losses', align: 'right', cell: (r) => formatInteger(record(r)?.lost) },
    { key: 't', header: 'T', title: 'Ties', align: 'right', cell: (r) => formatInteger(record(r)?.tied) },
    {
      key: 'nr',
      header: 'NR',
      title: 'No result',
      align: 'right',
      cell: (r) => formatInteger(record(r)?.noResult),
    },
    { key: 'pct', header: 'Win %', align: 'right', cell: (r) => formatPercent(record(r)?.winPercentage) },
  ];
}

const situationColumns: Column<SituationRow>[] = [
  { key: 'label', header: 'Situation', cell: (r) => r.label },
  ...recordColumns<SituationRow>((r) => r.record),
];

export function TeamOverviewTab() {
  const { team, query } = useTeamContext();
  const stats = useTeamStatsQuery(team.id, query);
  const seasons = useTeamSeasonsQuery(team.id, query);
  return (
    <div className="space-y-4">
      <QueryContent
        query={stats}
        loading={<StatCardSkeleton count={7} />}
        isEmpty={(s) => (s.overall?.played ?? 0) === 0}
        emptyTitle={NO_STATS}
      >
        {(s) => (
          <>
            <StatGrid>
              <StatCard label="Matches" value={formatInteger(s.overall?.played)} />
              <StatCard
                label="Wins"
                value={formatInteger(s.overall?.won)}
                hint={`${formatInteger(s.overall?.tied)} tied`}
              />
              <StatCard
                label="Losses"
                value={formatInteger(s.overall?.lost)}
                hint={`${formatInteger(s.overall?.noResult)} no result`}
              />
              <StatCard label="Win %" value={formatPercent(s.overall?.winPercentage)} />
              <StatCard
                label="Runs"
                value={formatInteger(s.runsFor)}
                hint={`Run rate ${formatDecimal(s.runRate)}`}
              />
              <StatCard
                label="Average score"
                value={formatDecimal(s.averageScore)}
                hint={`Conceded ${formatDecimal(s.averageConceded)}`}
              />
              <StatCard label="Highest score" value={formatInteger(s.highestScore)} />
            </StatGrid>
            <div className="grid gap-4 lg:grid-cols-2">
              <ChartCard title="Batting first vs chasing" description="Results by innings batted">
                <BarChart
                  height={260}
                  categories={['Batting first', 'Chasing']}
                  series={[
                    { name: 'Won', data: [s.battingFirst?.won, s.chasing?.won], color: '#059669' },
                    { name: 'Lost', data: [s.battingFirst?.lost, s.chasing?.lost], color: '#dc2626' },
                  ]}
                  ariaLabel={`${team.name} wins and losses batting first and chasing`}
                />
              </ChartCard>
              <Card>
                <CardHeader>
                  <CardTitle>Records by situation</CardTitle>
                </CardHeader>
                <CardContent>
                  <DataTable
                    caption={`${team.name} record overall, batting first, chasing, home and away`}
                    columns={situationColumns}
                    rows={[
                      { label: 'Overall', record: s.overall },
                      { label: 'Batting first', record: s.battingFirst },
                      { label: 'Chasing', record: s.chasing },
                      { label: 'Home', record: s.home },
                      { label: 'Away', record: s.away },
                    ]}
                    rowKey={(r) => r.label}
                  />
                </CardContent>
              </Card>
            </div>
          </>
        )}
      </QueryContent>
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Wins and losses by season">
          <QueryContent
            query={seasons}
            loading={<ChartSkeleton height={260} />}
            isEmpty={(d) => d.length === 0}
            emptyTitle={NO_STATS}
          >
            {(rows) => (
              <BarChart
                height={260}
                categories={rows.map((r) => r.season)}
                series={[
                  {
                    name: 'Won',
                    data: rows.map((r) => r.stats?.overall?.won),
                    stack: 'result',
                    color: '#059669',
                  },
                  {
                    name: 'Lost',
                    data: rows.map((r) => r.stats?.overall?.lost),
                    stack: 'result',
                    color: '#dc2626',
                  },
                ]}
                ariaLabel={`${team.name} wins and losses per season`}
              />
            )}
          </QueryContent>
        </ChartCard>
        <ChartCard title="Average score by season">
          <QueryContent
            query={seasons}
            loading={<ChartSkeleton height={260} />}
            isEmpty={(d) => d.length === 0}
            emptyTitle={NO_STATS}
          >
            {(rows) => (
              <LineChart
                height={260}
                categories={rows.map((r) => r.season)}
                series={[
                  { name: 'Scored', data: rows.map((r) => r.stats?.averageScore) },
                  { name: 'Conceded', data: rows.map((r) => r.stats?.averageConceded) },
                ]}
                ariaLabel={`${team.name} average score and average conceded per season`}
              />
            )}
          </QueryContent>
        </ChartCard>
      </div>
    </div>
  );
}

const seasonColumns: Column<TeamSeasonStats>[] = [
  {
    key: 'season',
    header: 'Season',
    cell: (r) => (
      <Link to={`/seasons/${r.season}`} className="font-medium text-primary hover:underline">
        {r.season}
      </Link>
    ),
  },
  ...recordColumns<TeamSeasonStats>((r) => r.stats?.overall),
  { key: 'avg', header: 'Avg score', align: 'right', cell: (r) => formatDecimal(r.stats?.averageScore) },
  {
    key: 'rr',
    header: 'RR',
    title: 'Run rate',
    align: 'right',
    cell: (r) => formatDecimal(r.stats?.runRate),
  },
  {
    key: 'hs',
    header: 'HS',
    title: 'Highest score',
    align: 'right',
    cell: (r) => formatInteger(r.stats?.highestScore),
  },
];

export function TeamSeasonsTab() {
  const { team, query } = useTeamContext();
  const seasons = useTeamSeasonsQuery(team.id, query);
  return (
    <Card>
      <CardContent className="pt-4 sm:pt-5">
        <QueryContent
          query={seasons}
          loading={<TableSkeleton columns={10} />}
          isEmpty={(d) => d.length === 0}
          emptyTitle={NO_STATS}
        >
          {(rows) => (
            <DataTable
              caption={`${team.name} season by season`}
              columns={seasonColumns}
              rows={[...rows].reverse()}
              rowKey={(r) => r.season}
            />
          )}
        </QueryContent>
      </CardContent>
    </Card>
  );
}

const PERFORMERS_LIMIT = 25;

const battingColumns: Column<BattingLeader>[] = [
  { key: 'rank', header: '#', align: 'right', cell: (r) => r.rank },
  { key: 'player', header: 'Batter', cell: (r) => <PlayerLink player={r.player} /> },
  { key: 'inn', header: 'Inns', title: 'Innings', align: 'right', cell: (r) => formatInteger(r.innings) },
  { key: 'runs', header: 'Runs', align: 'right', cell: (r) => formatInteger(r.runs) },
  { key: 'avg', header: 'Avg', title: 'Average', align: 'right', cell: (r) => formatDecimal(r.average) },
  { key: 'sr', header: 'SR', title: 'Strike rate', align: 'right', cell: (r) => formatDecimal(r.strikeRate) },
  { key: '50', header: '50s', align: 'right', cell: (r) => formatInteger(r.fifties) },
  { key: '100', header: '100s', align: 'right', cell: (r) => formatInteger(r.hundreds) },
];

const bowlingColumns: Column<BowlingLeader>[] = [
  { key: 'rank', header: '#', align: 'right', cell: (r) => r.rank },
  { key: 'player', header: 'Bowler', cell: (r) => <PlayerLink player={r.player} /> },
  { key: 'inn', header: 'Inns', title: 'Innings', align: 'right', cell: (r) => formatInteger(r.innings) },
  { key: 'overs', header: 'Overs', align: 'right', cell: (r) => r.overs },
  { key: 'wkts', header: 'Wkts', title: 'Wickets', align: 'right', cell: (r) => formatInteger(r.wickets) },
  { key: 'econ', header: 'Econ', title: 'Economy', align: 'right', cell: (r) => formatDecimal(r.economy) },
  { key: 'avg', header: 'Avg', title: 'Average', align: 'right', cell: (r) => formatDecimal(r.average) },
];

function TeamPerformers({ discipline }: { discipline: 'batting' | 'bowling' }) {
  const { team, query } = useTeamContext();
  const performers = useTeamTopPerformersQuery(team.id, query && { ...query, limit: PERFORMERS_LIMIT });
  const pick = (d: { batting?: BattingLeader[]; bowling?: BowlingLeader[] }) =>
    discipline === 'batting' ? (d.batting ?? []) : (d.bowling ?? []);
  return (
    <QueryContent
      query={performers}
      loading={<TableSkeleton columns={8} />}
      isEmpty={(d) => pick(d).length === 0}
      emptyTitle={NO_STATS}
    >
      {(d) => (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <Card>
            <CardHeader>
              <CardTitle>
                {discipline === 'batting' ? 'Leading run scorers' : 'Leading wicket takers'}
              </CardTitle>
            </CardHeader>
            <CardContent>
              {discipline === 'batting' ? (
                <DataTable
                  caption={`${team.name} leading run scorers`}
                  columns={battingColumns}
                  rows={d.batting ?? []}
                  rowKey={(r) => r.player?.id ?? r.rank}
                />
              ) : (
                <DataTable
                  caption={`${team.name} leading wicket takers`}
                  columns={bowlingColumns}
                  rows={d.bowling ?? []}
                  rowKey={(r) => r.player?.id ?? r.rank}
                />
              )}
            </CardContent>
          </Card>
          <ChartCard title={discipline === 'batting' ? 'Top 10 by runs' : 'Top 10 by wickets'}>
            <BarChart
              horizontal
              height={340}
              categories={pick(d)
                .slice(0, 10)
                .map((r) => r.player?.name ?? '')}
              series={[
                discipline === 'batting'
                  ? { name: 'Runs', data: (d.batting ?? []).slice(0, 10).map((r) => r.runs) }
                  : {
                      name: 'Wickets',
                      data: (d.bowling ?? []).slice(0, 10).map((r) => r.wickets),
                      color: '#059669',
                    },
              ]}
              ariaLabel={`${team.name} top 10 ${discipline === 'batting' ? 'run scorers' : 'wicket takers'}`}
            />
          </ChartCard>
        </div>
      )}
    </QueryContent>
  );
}

export function TeamBattingTab() {
  return <TeamPerformers discipline="batting" />;
}

export function TeamBowlingTab() {
  return <TeamPerformers discipline="bowling" />;
}

export function TeamVenuesTab() {
  const { team, query } = useTeamContext();
  const venues = useTeamVenuesQuery(team.id, query);
  const columns: Column<TeamVenueStats>[] = [
    { key: 'venue', header: 'Venue', cell: (r) => <VenueLink venue={r.venue} /> },
    { key: 'city', header: 'City', cell: (r) => r.venue?.city ?? '–' },
    ...recordColumns<TeamVenueStats>((r) => r.stats?.overall),
    { key: 'avg', header: 'Avg score', align: 'right', cell: (r) => formatDecimal(r.stats?.averageScore) },
    {
      key: 'hs',
      header: 'HS',
      title: 'Highest score',
      align: 'right',
      cell: (r) => formatInteger(r.stats?.highestScore),
    },
  ];
  return (
    <Card>
      <CardContent className="pt-4 sm:pt-5">
        <QueryContent
          query={venues}
          loading={<TableSkeleton columns={10} />}
          isEmpty={(d) => d.length === 0}
          emptyTitle={NO_STATS}
        >
          {(rows) => (
            <DataTable
              caption={`${team.name} record by venue`}
              columns={columns}
              rows={rows}
              rowKey={(r) => r.venue?.id ?? 0}
            />
          )}
        </QueryContent>
      </CardContent>
    </Card>
  );
}

export function TeamHeadToHeadTab() {
  const { team, query } = useTeamContext();
  const opponents = useTeamOpponentsQuery(team.id, query);
  const columns: Column<TeamOpponentStats>[] = [
    { key: 'opponent', header: 'Opponent', cell: (r) => <TeamLink team={r.opponent} /> },
    ...recordColumns<TeamOpponentStats>((r) => r.stats?.overall),
    { key: 'avg', header: 'Avg score', align: 'right', cell: (r) => formatDecimal(r.stats?.averageScore) },
    {
      key: 'conc',
      header: 'Avg conceded',
      align: 'right',
      cell: (r) => formatDecimal(r.stats?.averageConceded),
    },
    {
      key: 'link',
      header: 'Details',
      cell: (r) => (
        <Link
          to={`/head-to-head?team1=${team.code}&team2=${r.opponent?.code ?? ''}`}
          className="text-primary hover:underline"
        >
          Head to head
        </Link>
      ),
    },
  ];
  return (
    <Card>
      <CardContent className="pt-4 sm:pt-5">
        <QueryContent
          query={opponents}
          loading={<TableSkeleton columns={10} />}
          isEmpty={(d) => d.length === 0}
          emptyTitle={NO_STATS}
        >
          {(rows) => (
            <DataTable
              caption={`${team.name} record against each opponent`}
              columns={columns}
              rows={rows}
              rowKey={(r) => r.opponent?.id ?? 0}
            />
          )}
        </QueryContent>
      </CardContent>
    </Card>
  );
}
