import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import type { VenueTeam } from '@/api/venuesApi';
import { PlayerLink, TeamLink } from '@/components/cards/EntityLinks';
import { BarChart, ChartCard } from '@/components/charts';
import { ChartSkeleton, StatCardSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { Card, CardContent } from '@/components/ui/card';
import { PhaseSection, TossSection } from '@/features/insights/InsightComponents';
import { useBattingLeaderboardQuery, useBowlingLeaderboardQuery } from '@/hooks/useAnalyticsQueries';
import { usePhaseInsightsQuery, useTossInsightsQuery, useVenueTeamsQuery } from '@/hooks/useInsightQueries';
import { formatDecimal, formatInteger, formatPercent } from '@/lib/format';

interface VenueInsightsProps {
  venueId: number;
  venueName: string;
  seasons: { season?: number; seasonFrom?: number; seasonTo?: number };
  onSeasonSelect: (season: number) => void;
}

const TOP = 5;

/** Who does well here (franchises, batters, bowlers), and how the toss and phases play at the venue. */
export function VenueInsights({ venueId, venueName, seasons, onSeasonSelect }: VenueInsightsProps) {
  const navigate = useNavigate();
  const scoped = useMemo(() => ({ ...seasons, venue: venueId }), [seasons, venueId]);
  const teams = useVenueTeamsQuery(venueId, seasons);
  const batters = useBattingLeaderboardQuery(
    useMemo(() => ({ ...scoped, metric: 'runs' as const, limit: TOP }), [scoped]),
  );
  const bowlers = useBowlingLeaderboardQuery(
    useMemo(() => ({ ...scoped, metric: 'wickets' as const, limit: TOP }), [scoped]),
  );
  const toss = useTossInsightsQuery(scoped);
  const phases = usePhaseInsightsQuery(scoped);

  return (
    <div className="mt-6 space-y-6">
      <section aria-labelledby="venue-teams" className="space-y-3">
        <h2 id="venue-teams" className="text-lg font-semibold">
          Franchises at {venueName}
        </h2>
        <QueryContent
          query={teams}
          loading={<TableSkeleton rows={6} />}
          isEmpty={(d) => d.length === 0}
          emptyTitle="No matches here for the selected seasons"
        >
          {(rows) => (
            <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
              <Card>
                <CardContent className="pt-4 sm:pt-5">
                  <DataTable
                    caption={`Franchise records at ${venueName}`}
                    exportFileName={`${venueName} franchises`}
                    columns={[
                      { key: 't', header: 'Team', cell: (r: VenueTeam) => <TeamLink team={r.team} /> },
                      {
                        key: 'p',
                        header: 'M',
                        title: 'Matches',
                        align: 'right',
                        cell: (r) => formatInteger(r.played),
                      },
                      {
                        key: 'w',
                        header: 'W',
                        title: 'Won',
                        align: 'right',
                        cell: (r) => formatInteger(r.won),
                      },
                      {
                        key: 'l',
                        header: 'L',
                        title: 'Lost',
                        align: 'right',
                        cell: (r) => formatInteger(r.lost),
                      },
                      {
                        key: 'pct',
                        header: 'Win %',
                        align: 'right',
                        cell: (r) => formatPercent(r.winPercentage),
                      },
                      {
                        key: 'avg',
                        header: 'Avg score',
                        align: 'right',
                        cell: (r) => formatDecimal(r.averageScore),
                      },
                      {
                        key: 'hs',
                        header: 'HS',
                        title: 'Highest score',
                        align: 'right',
                        cell: (r) => formatInteger(r.highestScore),
                      },
                    ]}
                    rows={rows}
                    rowKey={(r) => r.team?.id ?? 0}
                  />
                </CardContent>
              </Card>
              <ChartCard title="Wins here" description="Click a franchise to open it">
                <BarChart
                  horizontal
                  height={Math.max(220, Math.min(rows.length, 10) * 28)}
                  categories={rows.slice(0, 10).map((r) => r.team?.code ?? '')}
                  series={[
                    { name: 'Won', data: rows.slice(0, 10).map((r) => r.won), stack: 'r', color: '#059669' },
                    {
                      name: 'Lost',
                      data: rows.slice(0, 10).map((r) => r.lost),
                      stack: 'r',
                      color: '#dc2626',
                    },
                  ]}
                  ariaLabel={`Franchise wins and losses at ${venueName}`}
                  onItemClick={(index) => {
                    const id = rows[index]?.team?.id;
                    if (id !== undefined) navigate(`/teams/${id}`);
                  }}
                />
              </ChartCard>
            </div>
          )}
        </QueryContent>
      </section>

      <section aria-labelledby="venue-players" className="grid gap-4 lg:grid-cols-2">
        <h2 id="venue-players" className="sr-only">
          Top performers at {venueName}
        </h2>
        <ChartCard title={`Most runs at ${venueName}`} description="Click a bar to open the player">
          <QueryContent
            query={batters}
            loading={<ChartSkeleton height={220} />}
            isEmpty={(d) => d.length === 0}
          >
            {(rows) => (
              <>
                <BarChart
                  horizontal
                  height={220}
                  categories={rows.map((r) => r.player?.name ?? '')}
                  series={[{ name: 'Runs', data: rows.map((r) => r.runs) }]}
                  ariaLabel={`Most runs at ${venueName}`}
                  onItemClick={(index) => {
                    const id = rows[index]?.player?.id;
                    if (id !== undefined) navigate(`/players/${id}`);
                  }}
                />
                <RankedLinks rows={rows.map((r) => ({ player: r.player, value: formatInteger(r.runs) }))} />
              </>
            )}
          </QueryContent>
        </ChartCard>
        <ChartCard title={`Most wickets at ${venueName}`} description="Click a bar to open the player">
          <QueryContent
            query={bowlers}
            loading={<ChartSkeleton height={220} />}
            isEmpty={(d) => d.length === 0}
          >
            {(rows) => (
              <>
                <BarChart
                  horizontal
                  height={220}
                  categories={rows.map((r) => r.player?.name ?? '')}
                  series={[{ name: 'Wickets', data: rows.map((r) => r.wickets), color: '#059669' }]}
                  ariaLabel={`Most wickets at ${venueName}`}
                  onItemClick={(index) => {
                    const id = rows[index]?.player?.id;
                    if (id !== undefined) navigate(`/players/${id}`);
                  }}
                />
                <RankedLinks
                  rows={rows.map((r) => ({ player: r.player, value: formatInteger(r.wickets) }))}
                />
              </>
            )}
          </QueryContent>
        </ChartCard>
      </section>

      <section aria-labelledby="venue-toss" className="space-y-3">
        <h2 id="venue-toss" className="text-lg font-semibold">
          Toss at {venueName}
        </h2>
        <QueryContent
          query={toss}
          loading={<StatCardSkeleton />}
          isEmpty={(d) => (d.overall?.matches ?? 0) === 0}
        >
          {(d) => <TossSection data={d} subject={venueName} onSeasonSelect={onSeasonSelect} />}
        </QueryContent>
      </section>

      <section aria-labelledby="venue-phases" className="space-y-3">
        <h2 id="venue-phases" className="text-lg font-semibold">
          Phases at {venueName}
        </h2>
        <QueryContent
          query={phases}
          loading={<ChartSkeleton />}
          isEmpty={(d) => (d.batting ?? []).every((p) => p.legalBalls === 0)}
        >
          {(d) => <PhaseSection data={d} subject={venueName} onSeasonSelect={onSeasonSelect} />}
        </QueryContent>
      </section>
    </div>
  );
}

/** The same ranking as links, so every chart drill-down has a keyboard and screen-reader equivalent. */
function RankedLinks({ rows }: { rows: { player?: { id: number; name?: string }; value: string }[] }) {
  return (
    <ol className="mt-2 grid gap-x-4 gap-y-1 text-sm sm:grid-cols-2">
      {rows.map((r, i) => (
        <li key={r.player?.id ?? i} className="flex justify-between gap-2">
          <span>
            {i + 1}. <PlayerLink player={r.player} />
          </span>
          <span className="tabular-nums text-muted-foreground">{r.value}</span>
        </li>
      ))}
    </ol>
  );
}
