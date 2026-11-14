import { useQueries } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { useMemo } from 'react';
import type { ComparedPlayer, ComparisonQuery, Discipline } from '@/api/analyticsApi';
import { playersApi } from '@/api/playersApi';
import { PlayerLink } from '@/components/cards/EntityLinks';
import { BarChart, ChartCard } from '@/components/charts';
import { FilterBar, PlayerPicker, SeasonRangeFilter, SelectFilter } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { TableSkeleton } from '@/components/loading/Skeletons';
import { EmptyState } from '@/components/states/EmptyState';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { usePlayerComparisonQuery } from '@/hooks/useAnalyticsQueries';
import { useUrlState } from '@/hooks/useUrlState';
import { formatDecimal, formatInteger, formatPercent, formatScore } from '@/lib/format';
import { queryKeys } from '@/lib/queryKeys';

export const MAX_COMPARED_PLAYERS = 5;
const DISCIPLINES = [
  { value: 'batting', label: 'Batting' },
  { value: 'bowling', label: 'Bowling' },
];

interface Metric {
  label: string;
  value: (p: ComparedPlayer) => string;
}

/** Only the metrics of the chosen discipline are shown. */
const METRICS: Record<Discipline, Metric[]> = {
  batting: [
    { label: 'Matches', value: (p) => formatInteger(p.batting?.matches) },
    { label: 'Innings', value: (p) => formatInteger(p.batting?.innings) },
    { label: 'Runs', value: (p) => formatInteger(p.batting?.runs) },
    { label: 'Balls', value: (p) => formatInteger(p.batting?.balls) },
    { label: 'Average', value: (p) => formatDecimal(p.batting?.average) },
    { label: 'Strike rate', value: (p) => formatDecimal(p.batting?.strikeRate) },
    { label: 'Fours', value: (p) => formatInteger(p.batting?.fours) },
    { label: 'Sixes', value: (p) => formatInteger(p.batting?.sixes) },
    { label: 'Fifties / hundreds', value: (p) => `${p.batting?.fifties ?? 0} / ${p.batting?.hundreds ?? 0}` },
    {
      label: 'Highest score',
      value: (p) =>
        p.batting?.highestScore === undefined
          ? '–'
          : formatScore(p.batting.highestScore, p.batting.highestScoreNotOut),
    },
    { label: 'Dot %', value: (p) => formatPercent(p.batting?.dotBallPercentage) },
    { label: 'Boundary %', value: (p) => formatPercent(p.batting?.boundaryPercentage) },
  ],
  bowling: [
    { label: 'Matches', value: (p) => formatInteger(p.bowling?.matches) },
    { label: 'Innings', value: (p) => formatInteger(p.bowling?.innings) },
    { label: 'Overs', value: (p) => p.bowling?.overs ?? '–' },
    { label: 'Wickets', value: (p) => formatInteger(p.bowling?.wickets) },
    { label: 'Economy', value: (p) => formatDecimal(p.bowling?.economy) },
    { label: 'Average', value: (p) => formatDecimal(p.bowling?.average) },
    { label: 'Strike rate', value: (p) => formatDecimal(p.bowling?.strikeRate) },
    { label: 'Best figures', value: (p) => p.bowling?.bestFigures ?? '–' },
    { label: 'Dot %', value: (p) => formatPercent(p.bowling?.dotBallPercentage) },
  ],
};

export function ComparePage() {
  const url = useUrlState();
  const ids = url.getNumbers('ids').slice(0, MAX_COMPARED_PLAYERS);
  const discipline: Discipline = url.get('discipline') === 'bowling' ? 'bowling' : 'batting';
  const seasonFrom = url.getNumber('seasonFrom');
  const seasonTo = url.getNumber('seasonTo');

  // Names for the chosen players before a comparison exists (cached per player).
  const names = useQueries({
    queries: ids.map((id) => ({ queryKey: queryKeys.players.detail(id), queryFn: () => playersApi.get(id) })),
  });

  // The ids are rebuilt on every render; their joined text keeps the query (and its cache key) stable.
  const idsKey = ids.join(',');
  const query = useMemo<ComparisonQuery | undefined>(() => {
    const chosen = idsKey ? idsKey.split(',').map(Number) : [];
    return chosen.length >= 2 ? { ids: chosen, discipline, seasonFrom, seasonTo } : undefined;
  }, [idsKey, discipline, seasonFrom, seasonTo]);
  const comparison = usePlayerComparisonQuery(query);
  const setIds = (next: number[]) => url.update({ ids: next.length ? next : undefined });

  return (
    <>
      <PageHeader
        title="Player Comparison"
        description={`Compare up to ${MAX_COMPARED_PLAYERS} players side by side.`}
      />
      <FilterBar
        onReset={() =>
          url.update({ ids: undefined, discipline: undefined, seasonFrom: undefined, seasonTo: undefined })
        }
        resetDisabled={ids.length === 0}
      >
        {ids.length < MAX_COMPARED_PLAYERS ? (
          <PlayerPicker label="Add player" excludeIds={ids} onSelect={(p) => setIds([...ids, p.id])} />
        ) : (
          <p className="self-center text-sm text-muted-foreground">
            Maximum of {MAX_COMPARED_PLAYERS} players reached.
          </p>
        )}
        <SelectFilter
          label="Discipline"
          value={discipline}
          options={DISCIPLINES}
          onChange={(v) => url.update({ discipline: v === 'bowling' ? 'bowling' : undefined })}
        />
        <SeasonRangeFilter
          from={seasonFrom}
          to={seasonTo}
          onChange={({ from, to }) => url.update({ seasonFrom: from, seasonTo: to })}
        />
      </FilterBar>

      {ids.length > 0 && (
        <ul aria-label="Selected players" className="mb-4 flex flex-wrap gap-2">
          {ids.map((id, index) => (
            <li key={id}>
              <Badge variant="outline" className="gap-1 py-1 pl-2 pr-1 text-sm">
                {names[index]?.data?.name ?? `Player ${id}`}
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-5"
                  aria-label={`Remove ${names[index]?.data?.name ?? `player ${id}`}`}
                  onClick={() => setIds(ids.filter((x) => x !== id))}
                >
                  <X aria-hidden="true" />
                </Button>
              </Badge>
            </li>
          ))}
        </ul>
      )}

      <QueryContent
        query={comparison}
        loading={<TableSkeleton rows={10} columns={ids.length + 1} />}
        idle={
          <EmptyState
            title="Choose at least two players"
            description="Search for players above to compare their figures."
          />
        }
      >
        {(d) => {
          const players = d.players ?? [];
          const keyMetric = discipline === 'batting' ? 'Runs' : 'Wickets';
          return (
            <div className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>{discipline === 'batting' ? 'Batting' : 'Bowling'} comparison</CardTitle>
                </CardHeader>
                <CardContent>
                  <DataTable
                    caption={`${discipline} figures of the selected players`}
                    columns={[
                      {
                        key: 'metric',
                        header: 'Metric',
                        cell: (m: Metric) => <span className="font-medium">{m.label}</span>,
                      },
                      ...players.map((p, i) => ({
                        key: `p${i}`,
                        header: p.player?.name ?? '',
                        align: 'right' as const,
                        cell: (m: Metric) => m.value(p),
                      })),
                    ]}
                    rows={METRICS[discipline]}
                    rowKey={(m) => m.label}
                  />
                  <p className="mt-2 flex flex-wrap gap-x-3 text-sm text-muted-foreground">
                    Profiles:{' '}
                    {players.map((p) => (
                      <PlayerLink key={p.player?.id} player={p.player} />
                    ))}
                  </p>
                </CardContent>
              </Card>
              <div className="grid gap-4 lg:grid-cols-2">
                <ChartCard title={keyMetric}>
                  <BarChart
                    height={260}
                    categories={players.map((p) => p.player?.name ?? '')}
                    series={[
                      {
                        name: keyMetric,
                        data: players.map((p) =>
                          discipline === 'batting' ? p.batting?.runs : p.bowling?.wickets,
                        ),
                      },
                    ]}
                    ariaLabel={`${keyMetric} of the selected players`}
                  />
                </ChartCard>
                <ChartCard
                  title={discipline === 'batting' ? 'Strike rate and average' : 'Economy and average'}
                >
                  <BarChart
                    height={260}
                    categories={players.map((p) => p.player?.name ?? '')}
                    series={
                      discipline === 'batting'
                        ? [
                            { name: 'Strike rate', data: players.map((p) => p.batting?.strikeRate) },
                            { name: 'Average', data: players.map((p) => p.batting?.average) },
                          ]
                        : [
                            { name: 'Economy', data: players.map((p) => p.bowling?.economy) },
                            { name: 'Average', data: players.map((p) => p.bowling?.average) },
                          ]
                    }
                    ariaLabel={`${discipline === 'batting' ? 'Strike rate and average' : 'Economy and average'} of the selected players`}
                  />
                </ChartCard>
              </div>
            </div>
          );
        }}
      </QueryContent>
    </>
  );
}
