import { useQueries } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import type { Discipline } from '@/api/analyticsApi';
import { playersApi, type PlayerRef } from '@/api/playersApi';
import { BarChart, ChartCard, LineChart, RadarChart } from '@/components/charts';
import { ChartSkeleton } from '@/components/loading/Skeletons';
import { DataTable } from '@/components/tables/DataTable';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PHASE_LABEL } from '@/lib/phases';
import { formatDecimal, formatPercent } from '@/lib/format';
import { queryKeys } from '@/lib/queryKeys';

interface ComparisonExtrasProps {
  players: PlayerRef[];
  discipline: Discipline;
  seasonFrom?: number;
  seasonTo?: number;
}

const PHASES = ['powerplay', 'middle', 'death'] as const;

/**
 * Deeper comparison of the chosen players: consistency, phases, season trend and a profile radar. One cached
 * request per player and resource (at most five players), shared with their player pages.
 */
export function ComparisonExtras({ players, discipline, seasonFrom, seasonTo }: ComparisonExtrasProps) {
  const navigate = useNavigate();
  const query = { seasonFrom, seasonTo };
  const consistency = useQueries({
    queries: players.map((p) => ({
      queryKey: queryKeys.players.resource(p.id, 'consistency', query),
      queryFn: ({ signal }: { signal: AbortSignal }) => playersApi.consistency(p.id, query, signal),
    })),
  });
  const phases = useQueries({
    queries: players.map((p) => ({
      queryKey: queryKeys.players.resource(p.id, 'phases', query),
      queryFn: ({ signal }: { signal: AbortSignal }) => playersApi.phases(p.id, query, signal),
    })),
  });
  const seasons = useQueries({
    queries: players.map((p) => ({
      queryKey: queryKeys.players.resource(p.id, discipline, query),
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        discipline === 'batting'
          ? playersApi.batting(p.id, query, signal)
          : playersApi.bowling(p.id, query, signal),
    })),
  });
  const loading = [...consistency, ...phases, ...seasons].some((q) => q.isPending);
  if (loading) return <ChartSkeleton height={260} label="Loading comparison details" />;

  const names = players.map((p) => p.name ?? `#${p.id}`);
  const batting = discipline === 'batting';
  const seasonData = seasons.map((q) => (q.data && 'bySeason' in q.data ? (q.data.bySeason ?? []) : []));
  const years = [...new Set(seasonData.flatMap((rows) => rows.map((r) => r.season)))].sort((a, b) => a - b);

  // Consistency rows: metric -> value per player.
  const consistencyRows = batting
    ? [
        {
          label: 'Median score',
          values: consistency.map((q) => formatDecimal(q.data?.batting?.medianScore)),
        },
        {
          label: 'Score spread (std dev)',
          values: consistency.map((q) => formatDecimal(q.data?.batting?.standardDeviation)),
        },
        {
          label: 'Variation (lower is steadier)',
          values: consistency.map((q) => formatPercent(q.data?.batting?.coefficientOfVariation)),
        },
        {
          label: '30+ innings',
          values: consistency.map((q) => formatPercent(q.data?.batting?.thirtyPlusPercentage)),
        },
        {
          label: '50+ innings',
          values: consistency.map((q) => formatPercent(q.data?.batting?.fiftyPlusPercentage)),
        },
        {
          label: 'Under 10',
          values: consistency.map((q) => formatPercent(q.data?.batting?.underTenPercentage)),
        },
      ]
    : [
        {
          label: 'Wicket-taking innings',
          values: consistency.map((q) => formatPercent(q.data?.bowling?.wicketInningsPercentage)),
        },
        {
          label: '2+ wicket innings',
          values: consistency.map((q) => formatPercent(q.data?.bowling?.multiWicketInningsPercentage)),
        },
        {
          label: 'Economy spread (std dev)',
          values: consistency.map((q) => formatDecimal(q.data?.bowling?.economyStandardDeviation)),
        },
        {
          label: 'Economical innings (under 8)',
          values: consistency.map((q) => formatPercent(q.data?.bowling?.economicalInningsPercentage)),
        },
      ];

  // Radar: each axis scaled to the best of the compared players (presentation only).
  const radarMetrics = batting
    ? [
        {
          name: 'Average',
          values: seasons.map((q) =>
            q.data && 'summary' in q.data ? numberOf(q.data.summary, 'average') : undefined,
          ),
        },
        {
          name: 'Strike rate',
          values: seasons.map((q) =>
            q.data && 'summary' in q.data ? numberOf(q.data.summary, 'strikeRate') : undefined,
          ),
        },
        {
          name: 'Boundary %',
          values: seasons.map((q) =>
            q.data && 'summary' in q.data ? numberOf(q.data.summary, 'boundaryPercentage') : undefined,
          ),
        },
        { name: '30+ innings %', values: consistency.map((q) => q.data?.batting?.thirtyPlusPercentage) },
      ]
    : [
        {
          name: 'Wickets',
          values: seasons.map((q) =>
            q.data && 'summary' in q.data ? numberOf(q.data.summary, 'wickets') : undefined,
          ),
        },
        {
          name: 'Dot %',
          values: seasons.map((q) =>
            q.data && 'summary' in q.data ? numberOf(q.data.summary, 'dotBallPercentage') : undefined,
          ),
        },
        {
          name: 'Wicket innings %',
          values: consistency.map((q) => q.data?.bowling?.wicketInningsPercentage),
        },
        {
          name: 'Economical %',
          values: consistency.map((q) => q.data?.bowling?.economicalInningsPercentage),
        },
      ];

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Consistency</CardTitle>
          <CardDescription>How steady each player is, not only how productive</CardDescription>
        </CardHeader>
        <CardContent>
          <DataTable
            caption={`${discipline} consistency of the selected players`}
            exportFileName={`${discipline} consistency comparison`}
            columns={[
              {
                key: 'm',
                header: 'Metric',
                cell: (r: { label: string; values: string[] }) => (
                  <span className="font-medium">{r.label}</span>
                ),
              },
              ...names.map((name, i) => ({
                key: `p${i}`,
                header: name,
                align: 'right' as const,
                cell: (r: { values: string[] }) => r.values[i],
              })),
            ]}
            rows={consistencyRows}
            rowKey={(r) => r.label}
          />
        </CardContent>
      </Card>
      <div className="grid gap-4 xl:grid-cols-2">
        <ChartCard
          title={batting ? 'Strike rate by phase' : 'Economy by phase'}
          description="Click a bar to open the player"
        >
          <BarChart
            height={280}
            categories={PHASES.map((p) => PHASE_LABEL[p] ?? p)}
            series={phases.map((q, i) => ({
              name: names[i] ?? '',
              data: PHASES.map((phase) =>
                batting
                  ? q.data?.batting?.find((p) => p.phase === phase)?.strikeRate
                  : q.data?.bowling?.find((p) => p.phase === phase)?.economy,
              ),
            }))}
            ariaLabel={`${batting ? 'Strike rate' : 'Economy'} by phase of the selected players`}
          />
        </ChartCard>
        <ChartCard title="Profile" description="Each axis scaled to the best of the players compared">
          <RadarChart
            height={280}
            indicators={radarMetrics.map((m) => ({
              name: m.name,
              max: Math.max(1, ...m.values.map((v) => (typeof v === 'number' ? v : 0))) * 1.1,
            }))}
            series={names.map((name, i) => ({ name, values: radarMetrics.map((m) => m.values[i]) }))}
            ariaLabel={`Profile of the selected players: ${radarMetrics.map((m) => m.name).join(', ')}`}
          />
        </ChartCard>
      </div>
      <ChartCard
        title={batting ? 'Runs by season' : 'Wickets by season'}
        description="Click a season to see the leaderboard for it"
      >
        <LineChart
          height={280}
          zoom={years.length > 12}
          categories={years}
          series={seasonData.map((rows, i) => ({
            name: names[i] ?? '',
            data: years.map((year) => {
              const stats = rows.find((r) => r.season === year)?.stats;
              return stats ? numberOf(stats, batting ? 'runs' : 'wickets') : null;
            }),
          }))}
          ariaLabel={`${batting ? 'Runs' : 'Wickets'} by season of the selected players`}
          onItemClick={(_, season) =>
            navigate(`/leaderboards?${batting ? '' : 'type=bowling&'}season=${season}`)
          }
        />
      </ChartCard>
    </div>
  );
}

function numberOf(object: object | undefined, key: string): number | undefined {
  const value = object ? (object as Record<string, unknown>)[key] : undefined;
  return typeof value === 'number' ? value : undefined;
}
