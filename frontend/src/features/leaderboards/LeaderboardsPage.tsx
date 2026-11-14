import type { UseQueryResult } from '@tanstack/react-query';
import { useMemo } from 'react';
import type { BattingLeader, BattingMetric, BowlingLeader, BowlingMetric } from '@/api/analyticsApi';
import { PlayerLink } from '@/components/cards/EntityLinks';
import { BarChart, ChartCard } from '@/components/charts';
import { FilterBar, SeasonFilter, SeasonRangeFilter, SelectFilter, TeamFilter } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { Input, Label } from '@/components/ui/form';
import { Card, CardContent } from '@/components/ui/card';
import { useBattingLeaderboardQuery, useBowlingLeaderboardQuery } from '@/hooks/useAnalyticsQueries';
import { useStatsFilters, type StatsFilterState } from '@/hooks/useStatsFilters';
import { formatDecimal, formatInteger } from '@/lib/format';

const BATTING_METRICS: { value: BattingMetric; label: string }[] = [
  { value: 'runs', label: 'Runs' },
  { value: 'average', label: 'Average' },
  { value: 'strikeRate', label: 'Strike rate' },
  { value: 'fours', label: 'Fours' },
  { value: 'sixes', label: 'Sixes' },
  { value: 'hundreds', label: 'Hundreds' },
  { value: 'fifties', label: 'Fifties' },
];
const BOWLING_METRICS: { value: BowlingMetric; label: string }[] = [
  { value: 'wickets', label: 'Wickets' },
  { value: 'economy', label: 'Economy' },
  { value: 'average', label: 'Average' },
  { value: 'strikeRate', label: 'Strike rate' },
  { value: 'dotBalls', label: 'Dot balls' },
];
const LIMITS = ['10', '25', '50', '100'].map((v) => ({ value: v, label: `Top ${v}` }));
const FILTER_KEYS = [
  'metric',
  'season',
  'seasonFrom',
  'seasonTo',
  'team',
  'minimumBalls',
  'minimumOvers',
  'limit',
];

const battingColumns: Column<BattingLeader>[] = [
  { key: 'rank', header: '#', align: 'right', cell: (r) => r.rank },
  { key: 'player', header: 'Batter', cell: (r) => <PlayerLink player={r.player} /> },
  {
    key: 'value',
    header: 'Value',
    align: 'right',
    cell: (r) => <span className="font-semibold">{formatDecimal(r.value)}</span>,
  },
  { key: 'inn', header: 'Inns', title: 'Innings', align: 'right', cell: (r) => formatInteger(r.innings) },
  { key: 'runs', header: 'Runs', align: 'right', cell: (r) => formatInteger(r.runs) },
  { key: 'balls', header: 'Balls', align: 'right', cell: (r) => formatInteger(r.balls) },
  { key: 'avg', header: 'Avg', title: 'Average', align: 'right', cell: (r) => formatDecimal(r.average) },
  { key: 'sr', header: 'SR', title: 'Strike rate', align: 'right', cell: (r) => formatDecimal(r.strikeRate) },
  { key: '4', header: '4s', align: 'right', cell: (r) => formatInteger(r.fours) },
  { key: '6', header: '6s', align: 'right', cell: (r) => formatInteger(r.sixes) },
  { key: '50', header: '50s', align: 'right', cell: (r) => formatInteger(r.fifties) },
  { key: '100', header: '100s', align: 'right', cell: (r) => formatInteger(r.hundreds) },
];

const bowlingColumns: Column<BowlingLeader>[] = [
  { key: 'rank', header: '#', align: 'right', cell: (r) => r.rank },
  { key: 'player', header: 'Bowler', cell: (r) => <PlayerLink player={r.player} /> },
  {
    key: 'value',
    header: 'Value',
    align: 'right',
    cell: (r) => <span className="font-semibold">{formatDecimal(r.value)}</span>,
  },
  { key: 'inn', header: 'Inns', title: 'Innings', align: 'right', cell: (r) => formatInteger(r.innings) },
  { key: 'overs', header: 'Overs', align: 'right', cell: (r) => r.overs },
  { key: 'wkts', header: 'Wkts', title: 'Wickets', align: 'right', cell: (r) => formatInteger(r.wickets) },
  { key: 'econ', header: 'Econ', title: 'Economy', align: 'right', cell: (r) => formatDecimal(r.economy) },
  { key: 'avg', header: 'Avg', title: 'Average', align: 'right', cell: (r) => formatDecimal(r.average) },
  { key: 'sr', header: 'SR', title: 'Strike rate', align: 'right', cell: (r) => formatDecimal(r.strikeRate) },
  { key: 'dots', header: 'Dots', title: 'Dot balls', align: 'right', cell: (r) => formatInteger(r.dotBalls) },
];

function MinimumFilter({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number | undefined;
  onChange: (v: number | undefined) => void;
}) {
  return (
    <div className="flex w-36 flex-col gap-1">
      <Label htmlFor={`min-${label}`}>{label}</Label>
      <Input
        id={`min-${label}`}
        type="number"
        min={0}
        inputMode="numeric"
        placeholder="Default"
        value={value ?? ''}
        onChange={(e) => {
          const n = Number(e.target.value);
          onChange(e.target.value === '' || !Number.isInteger(n) || n < 0 ? undefined : n);
        }}
      />
    </div>
  );
}

function SharedFilters({ state }: { state: StatsFilterState }) {
  const { filters, url } = state;
  return (
    <>
      <SeasonFilter
        value={filters.season}
        onChange={(season) => url.update({ season, seasonFrom: undefined, seasonTo: undefined })}
      />
      <SeasonRangeFilter
        from={filters.season === undefined ? filters.seasonFrom : undefined}
        to={filters.season === undefined ? filters.seasonTo : undefined}
        onChange={({ from, to }) => url.update({ season: undefined, seasonFrom: from, seasonTo: to })}
      />
      <TeamFilter value={filters.team} onChange={(team) => url.update({ team })} />
    </>
  );
}

/**
 * Ranking happens on the server: the page sends the metric, filters and qualification and shows the rows in the
 * order returned. Leaving the minimum empty uses the API default for the metric (shown in the results note).
 */
export function LeaderboardsPage() {
  const state = useStatsFilters();
  const { url } = state;
  const tab = url.get('type') === 'bowling' ? 'bowling' : 'batting';
  return (
    <>
      <PageHeader title="Leaderboards" description="IPL leaders by any batting or bowling metric." />
      <nav aria-label="Leaderboard type" className="mb-6 flex gap-1 border-b">
        {(['batting', 'bowling'] as const).map((t) => (
          <button
            key={t}
            type="button"
            aria-current={tab === t ? 'page' : undefined}
            onClick={() =>
              url.update({
                type: t === 'batting' ? undefined : t,
                metric: undefined,
                minimumBalls: undefined,
                minimumOvers: undefined,
              })
            }
            className={
              '-mb-px h-10 border-b-2 px-3 text-sm font-medium capitalize ' +
              (tab === t
                ? 'border-primary text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground')
            }
          >
            {t}
          </button>
        ))}
      </nav>
      {tab === 'batting' ? <BattingBoard state={state} /> : <BowlingBoard state={state} />}
    </>
  );
}

function BattingBoard({ state }: { state: StatsFilterState }) {
  const { url } = state;
  const metricParam = url.get('metric');
  const metric = BATTING_METRICS.find((m) => m.value === metricParam)?.value ?? 'runs';
  const minimumBalls = url.getNumber('minimumBalls');
  const limit = url.getNumber('limit') ?? 10;
  const query = useMemo(
    () =>
      state.query && {
        season: state.query.season,
        seasonFrom: state.query.seasonFrom,
        seasonTo: state.query.seasonTo,
        team: state.query.team,
        metric,
        minimumBalls,
        limit,
      },
    [state.query, metric, minimumBalls, limit],
  );
  const board = useBattingLeaderboardQuery(query);
  const label = BATTING_METRICS.find((m) => m.value === metric)?.label ?? metric;
  return (
    <>
      <FilterBar
        onReset={() => url.update(Object.fromEntries(FILTER_KEYS.map((k) => [k, undefined])))}
        resetDisabled={!FILTER_KEYS.some((k) => url.get(k))}
      >
        <SelectFilter
          label="Metric"
          value={metric}
          options={BATTING_METRICS}
          onChange={(v) => url.update({ metric: v === 'runs' ? undefined : v })}
        />
        <SharedFilters state={state} />
        <MinimumFilter
          label="Minimum balls"
          value={minimumBalls}
          onChange={(v) => url.update({ minimumBalls: v })}
        />
        <SelectFilter
          label="Show"
          value={String(limit)}
          options={LIMITS}
          onChange={(v) => url.update({ limit: v === '10' ? undefined : v })}
        />
      </FilterBar>
      <Board
        query={board}
        metricLabel={label}
        columns={battingColumns}
        rowKey={(r) => r.player?.id ?? r.rank}
        name={(r) => r.player?.name ?? ''}
        value={(r) => r.value}
        caption={`Batting leaderboard by ${label}`}
      />
    </>
  );
}

function BowlingBoard({ state }: { state: StatsFilterState }) {
  const { url } = state;
  const metricParam = url.get('metric');
  const metric = BOWLING_METRICS.find((m) => m.value === metricParam)?.value ?? 'wickets';
  const minimumOvers = url.getNumber('minimumOvers');
  const limit = url.getNumber('limit') ?? 10;
  const query = useMemo(
    () =>
      state.query && {
        season: state.query.season,
        seasonFrom: state.query.seasonFrom,
        seasonTo: state.query.seasonTo,
        team: state.query.team,
        metric,
        minimumOvers,
        limit,
      },
    [state.query, metric, minimumOvers, limit],
  );
  const board = useBowlingLeaderboardQuery(query);
  const label = BOWLING_METRICS.find((m) => m.value === metric)?.label ?? metric;
  return (
    <>
      <FilterBar
        onReset={() => url.update(Object.fromEntries(FILTER_KEYS.map((k) => [k, undefined])))}
        resetDisabled={!FILTER_KEYS.some((k) => url.get(k))}
      >
        <SelectFilter
          label="Metric"
          value={metric}
          options={BOWLING_METRICS}
          onChange={(v) => url.update({ metric: v === 'wickets' ? undefined : v })}
        />
        <SharedFilters state={state} />
        <MinimumFilter
          label="Minimum overs"
          value={minimumOvers}
          onChange={(v) => url.update({ minimumOvers: v })}
        />
        <SelectFilter
          label="Show"
          value={String(limit)}
          options={LIMITS}
          onChange={(v) => url.update({ limit: v === '10' ? undefined : v })}
        />
      </FilterBar>
      <Board
        query={board}
        metricLabel={label}
        columns={bowlingColumns}
        rowKey={(r) => r.player?.id ?? r.rank}
        name={(r) => r.player?.name ?? ''}
        value={(r) => r.value}
        caption={`Bowling leaderboard by ${label}`}
      />
    </>
  );
}

interface BoardProps<T> {
  query: UseQueryResult<T[]>;
  metricLabel: string;
  columns: Column<T>[];
  rowKey: (row: T) => number;
  name: (row: T) => string;
  value: (row: T) => number | undefined;
  caption: string;
}

const CHART_ROWS = 15;

function Board<T>({ query, metricLabel, columns, rowKey, name, value, caption }: BoardProps<T>) {
  return (
    <QueryContent
      query={query}
      loading={<TableSkeleton rows={10} columns={10} />}
      isEmpty={(rows) => rows.length === 0}
      emptyTitle="No statistics available for the selected filters"
      emptyDescription="Nobody qualifies. Try a wider season range or a lower minimum."
    >
      {(rows) => (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <Card>
            <CardContent className="pt-4 sm:pt-5">
              <DataTable caption={caption} columns={columns} rows={rows} rowKey={rowKey} />
            </CardContent>
          </Card>
          <ChartCard
            title={metricLabel}
            description={rows.length > CHART_ROWS ? `Top ${CHART_ROWS} shown` : undefined}
          >
            <BarChart
              horizontal
              height={Math.max(240, Math.min(rows.length, CHART_ROWS) * 26)}
              categories={rows.slice(0, CHART_ROWS).map(name)}
              series={[{ name: metricLabel, data: rows.slice(0, CHART_ROWS).map(value) }]}
              ariaLabel={`${caption}, top ${Math.min(rows.length, CHART_ROWS)}`}
            />
          </ChartCard>
        </div>
      )}
    </QueryContent>
  );
}
