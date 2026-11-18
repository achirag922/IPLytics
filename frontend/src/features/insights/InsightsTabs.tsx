import { useMemo } from 'react';
import type { SeasonInsight } from '@/api/analyticsApi';
import { TeamLink } from '@/components/cards/EntityLinks';
import { ChartCard, LineChart } from '@/components/charts';
import { ChartSkeleton, StatCardSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useInsightsContext } from '@/features/detailContext';
import {
  useBattingFirstInsightsQuery,
  usePhaseInsightsQuery,
  useSeasonComparisonQuery,
  useTossInsightsQuery,
} from '@/hooks/useInsightQueries';
import { useSeasonsQuery } from '@/hooks/useSeasonQueries';
import { useTeamLookup } from '@/hooks/useTeamQueries';
import { formatDecimal, formatInteger, formatPercent } from '@/lib/format';
import { BattingFirstSection, PhaseSection, TossSection } from './InsightComponents';

const NO_STATS = 'No statistics available for the selected filters';

/** Readable scope ("MI v CSK, 2020–2024") for chart labels and file names. */
function useScopeLabel(): string {
  const { filters } = useInsightsContext();
  const teams = useTeamLookup();
  const parts: string[] = [];
  if (filters.team) parts.push(teams.byCode(filters.team)?.code ?? filters.team);
  if (filters.opponent) parts.push(`v ${teams.byCode(filters.opponent)?.code ?? filters.opponent}`);
  if (filters.season !== undefined) parts.push(String(filters.season));
  else if (filters.seasonFrom !== undefined || filters.seasonTo !== undefined)
    parts.push(`${filters.seasonFrom ?? 'first'}–${filters.seasonTo ?? 'latest'}`);
  return parts.length ? parts.join(' ') : 'all IPL matches';
}

/** Cross-filter: focus the whole insights page on one season. */
function useSeasonSelect() {
  const { url } = useInsightsContext();
  return (season: number) => url.update({ season, seasonFrom: undefined, seasonTo: undefined });
}

export function TossTab() {
  const { query } = useInsightsContext();
  const toss = useTossInsightsQuery(query);
  const subject = useScopeLabel();
  const select = useSeasonSelect();
  return (
    <QueryContent
      query={toss}
      loading={<StatCardSkeleton />}
      isEmpty={(d) => (d.overall?.matches ?? 0) === 0}
      emptyTitle={NO_STATS}
    >
      {(d) => <TossSection data={d} subject={subject} onSeasonSelect={select} />}
    </QueryContent>
  );
}

export function ChasingTab() {
  const { query } = useInsightsContext();
  const chasing = useBattingFirstInsightsQuery(query);
  const subject = useScopeLabel();
  const select = useSeasonSelect();
  return (
    <QueryContent
      query={chasing}
      loading={<StatCardSkeleton />}
      isEmpty={(d) => (d.overall?.decisiveMatches ?? 0) === 0}
      emptyTitle={NO_STATS}
    >
      {(d) => <BattingFirstSection data={d} subject={subject} onSeasonSelect={select} />}
    </QueryContent>
  );
}

export function PhasesTab() {
  const { query, filters } = useInsightsContext();
  const phases = usePhaseInsightsQuery(query);
  const subject = useScopeLabel();
  const select = useSeasonSelect();
  return (
    <QueryContent
      query={phases}
      loading={<ChartSkeleton />}
      isEmpty={(d) => (d.batting ?? []).every((p) => p.legalBalls === 0)}
      emptyTitle={NO_STATS}
    >
      {(d) => (
        <PhaseSection
          data={d}
          subject={subject}
          battingLabel={filters.team ? `${filters.team} batting` : 'Scoring'}
          bowlingLabel={filters.team ? `${filters.team} bowling` : undefined}
          onSeasonSelect={select}
        />
      )}
    </QueryContent>
  );
}

const MAX_COMPARED = 10;

interface MetricRow {
  label: string;
  value: (s: SeasonInsight) => string;
  change?: number | null;
  percent?: boolean;
}

/**
 * Season comparison: pick 2 to 10 seasons (URL "compare"), compared side by side with the backend's change from
 * the first to the last; trend lines over the selected range, where clicking a season adds or removes it.
 */
export function SeasonsTab() {
  const { url, filters } = useInsightsContext();
  const seasons = useSeasonsQuery();
  const years = useMemo(
    () => [...(seasons.data ?? [])].map((s) => s.year).sort((a, b) => a - b),
    [seasons.data],
  );
  const chosen = url.getNumbers('compare').slice(0, MAX_COMPARED);
  const compare =
    chosen.length >= 2
      ? chosen
      : years.length >= 2
        ? [years[0] as number, years[years.length - 1] as number]
        : [];
  const compareKey = [...compare].sort((a, b) => a - b).join(',');

  // Query keys are compared by value, so these plain objects never cause extra requests.
  const compareList = compareKey ? compareKey.split(',').map(Number) : [];
  const comparison = useSeasonComparisonQuery(compareList.length >= 2 ? { seasons: compareList } : undefined);
  const trends = useSeasonComparisonQuery({
    seasonFrom: filters.season ?? filters.seasonFrom,
    seasonTo: filters.season ?? filters.seasonTo,
  });
  const toggle = (year: number) => {
    const next = compare.includes(year)
      ? compare.filter((y) => y !== year)
      : [...compare, year].slice(-MAX_COMPARED);
    url.update({ compare: next.length ? [...next].sort((a, b) => a - b) : undefined });
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Compare seasons</CardTitle>
          <CardDescription>
            Choose 2 to {MAX_COMPARED} seasons. Team, opponent and venue filters do not apply here.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div role="group" aria-label="Seasons to compare" className="flex flex-wrap gap-1.5">
            {years.map((year) => (
              <button
                key={year}
                type="button"
                aria-pressed={compare.includes(year)}
                onClick={() => toggle(year)}
                className="rounded-full border px-2.5 py-1 text-xs font-medium tabular-nums aria-pressed:border-primary aria-pressed:bg-primary aria-pressed:text-primary-foreground"
              >
                {year}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      <QueryContent
        query={comparison}
        loading={<TableSkeleton rows={12} />}
        isEmpty={(d) => (d.seasons ?? []).length === 0}
        emptyTitle={NO_STATS}
      >
        {(d) => {
          const rows = d.seasons ?? [];
          const c = d.change;
          const metrics: MetricRow[] = [
            { label: 'Matches', value: (s) => formatInteger(s.matches) },
            { label: 'Champion', value: (s) => s.champion?.name ?? '–' },
            { label: 'Average score', value: (s) => formatDecimal(s.averageScore), change: c?.averageScore },
            { label: 'Run rate', value: (s) => formatDecimal(s.runRate), change: c?.runRate },
            {
              label: 'Avg 1st-innings score',
              value: (s) => formatDecimal(s.averageFirstInningsScore),
              change: c?.averageFirstInningsScore,
            },
            {
              label: 'Fours per match',
              value: (s) => formatDecimal(s.foursPerMatch),
              change: c?.foursPerMatch,
            },
            {
              label: 'Sixes per match',
              value: (s) => formatDecimal(s.sixesPerMatch),
              change: c?.sixesPerMatch,
            },
            {
              label: 'Wickets per match',
              value: (s) => formatDecimal(s.wicketsPerMatch),
              change: c?.wicketsPerMatch,
            },
            {
              label: 'Powerplay run rate',
              value: (s) => formatDecimal(s.powerplayRunRate),
              change: c?.powerplayRunRate,
            },
            {
              label: 'Middle-overs run rate',
              value: (s) => formatDecimal(s.middleRunRate),
              change: c?.middleRunRate,
            },
            {
              label: 'Death-overs run rate',
              value: (s) => formatDecimal(s.deathRunRate),
              change: c?.deathRunRate,
            },
            {
              label: 'Batting first won',
              value: (s) => formatPercent(s.battingFirstWinPercentage),
              change: c?.battingFirstWinPercentage,
              percent: true,
            },
            {
              label: 'Toss winner won',
              value: (s) => formatPercent(s.tossWinnerWinPercentage),
              change: c?.tossWinnerWinPercentage,
              percent: true,
            },
            { label: 'Highest score', value: (s) => formatInteger(s.highestScore) },
          ];
          return (
            <Card>
              <CardHeader>
                <CardTitle>Side by side</CardTitle>
                {c && (
                  <CardDescription>
                    Change: {c.to} compared with {c.from}
                  </CardDescription>
                )}
              </CardHeader>
              <CardContent>
                <DataTable
                  caption={`Season comparison ${rows.map((s) => s.season).join(', ')}`}
                  exportFileName={`season comparison ${rows.map((s) => s.season).join(' ')}`}
                  columns={[
                    {
                      key: 'm',
                      header: 'Metric',
                      cell: (m: MetricRow) => <span className="font-medium">{m.label}</span>,
                    },
                    ...rows.map((s) => ({
                      key: String(s.season),
                      header: String(s.season),
                      align: 'right' as const,
                      cell: (m: MetricRow) =>
                        m.label === 'Champion' ? <TeamLink team={s.champion} short /> : m.value(s),
                    })),
                    ...(c
                      ? [
                          {
                            key: 'change',
                            header: 'Change',
                            align: 'right' as const,
                            cell: (m: MetricRow) =>
                              m.change === undefined || m.change === null
                                ? ''
                                : `${m.change > 0 ? '+' : ''}${formatDecimal(m.change)}${m.percent ? ' pts' : ''}`,
                          },
                        ]
                      : []),
                  ]}
                  rows={metrics}
                  rowKey={(m) => m.label}
                />
              </CardContent>
            </Card>
          );
        }}
      </QueryContent>

      <QueryContent
        query={trends}
        loading={<ChartSkeleton />}
        isEmpty={(d) => (d.seasons ?? []).length === 0}
        emptyTitle={NO_STATS}
      >
        {(d) => {
          const rows = d.seasons ?? [];
          const categories = rows.map((s) => s.season);
          const click = (_: number, season: string | number) => toggle(Number(season));
          return (
            <div className="grid gap-4 xl:grid-cols-2">
              <ChartCard title="Scoring trend" description="Click a season to add it to the comparison">
                <LineChart
                  height={260}
                  zoom={rows.length > 12}
                  categories={categories}
                  series={[
                    { name: 'Average score', data: rows.map((s) => s.averageScore) },
                    { name: 'Avg 1st-innings score', data: rows.map((s) => s.averageFirstInningsScore) },
                  ]}
                  ariaLabel="Average score by season"
                  onItemClick={click}
                />
              </ChartCard>
              <ChartCard title="Run rate by phase">
                <LineChart
                  height={260}
                  zoom={rows.length > 12}
                  categories={categories}
                  series={[
                    { name: 'Powerplay', data: rows.map((s) => s.powerplayRunRate) },
                    { name: 'Middle', data: rows.map((s) => s.middleRunRate) },
                    { name: 'Death', data: rows.map((s) => s.deathRunRate) },
                  ]}
                  ariaLabel="Run rate by phase and season"
                  onItemClick={click}
                />
              </ChartCard>
              <ChartCard title="Boundaries per match">
                <LineChart
                  height={260}
                  zoom={rows.length > 12}
                  categories={categories}
                  series={[
                    { name: 'Fours', data: rows.map((s) => s.foursPerMatch) },
                    { name: 'Sixes', data: rows.map((s) => s.sixesPerMatch) },
                  ]}
                  ariaLabel="Fours and sixes per match by season"
                  onItemClick={click}
                />
              </ChartCard>
              <ChartCard title="Toss and batting order">
                <LineChart
                  height={260}
                  zoom={rows.length > 12}
                  categories={categories}
                  series={[
                    { name: 'Batting first won %', data: rows.map((s) => s.battingFirstWinPercentage) },
                    { name: 'Toss winner won %', data: rows.map((s) => s.tossWinnerWinPercentage) },
                  ]}
                  ariaLabel="Batting-first and toss-winner win percentages by season"
                  onItemClick={click}
                />
              </ChartCard>
            </div>
          );
        }}
      </QueryContent>
    </div>
  );
}
