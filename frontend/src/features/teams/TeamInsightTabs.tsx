import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import type { MatchSummary } from '@/api/matchesApi';
import { BarChart, ChartCard } from '@/components/charts';
import { ChartSkeleton, StatCardSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useTeamContext } from '@/features/detailContext';
import { PhaseSection, TossSection } from '@/features/insights/InsightComponents';
import { matchColumns } from '@/features/matches/matchColumns';
import { usePhaseInsightsQuery, useTossInsightsQuery } from '@/hooks/useInsightQueries';
import { useMatchesQuery } from '@/hooks/useMatchQueries';
import { useTeamSeasonsQuery } from '@/hooks/useTeamQueries';
import { useUrlState } from '@/hooks/useUrlState';

const NO_STATS = 'No statistics available for the selected filters';
const FORM_MATCHES = 10;

export function TeamPhasesTab() {
  const { team, query } = useTeamContext();
  const url = useUrlState();
  const phases = usePhaseInsightsQuery(useMemo(() => query && { ...query, team: team.id }, [query, team.id]));
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
          subject={team.name ?? ''}
          battingLabel={`${team.code} batting`}
          bowlingLabel={`${team.code} bowling`}
          onSeasonSelect={(season) => url.update({ season, seasonFrom: undefined, seasonTo: undefined })}
        />
      )}
    </QueryContent>
  );
}

/** W / L / T / NR from the team's point of view, as text (never colour alone). */
function outcome(
  match: MatchSummary,
  teamId: number,
): { label: string; variant: 'success' | 'destructive' | 'secondary' } {
  if (match.result === 'no result') return { label: 'NR', variant: 'secondary' };
  const won = match.winner?.id === teamId;
  if (match.result === 'tie')
    return { label: won ? 'T (won)' : 'T (lost)', variant: won ? 'success' : 'destructive' };
  return won ? { label: 'W', variant: 'success' } : { label: 'L', variant: 'destructive' };
}

export function TeamTossTab() {
  const { team, query } = useTeamContext();
  const navigate = useNavigate();
  const toss = useTossInsightsQuery(useMemo(() => query && { ...query, team: team.id }, [query, team.id]));
  const form = useMatchesQuery(
    useMemo(() => query && { ...query, team: team.id, size: FORM_MATCHES }, [query, team.id]),
  );
  const seasons = useTeamSeasonsQuery(team.id, query);
  return (
    <div className="space-y-6">
      <section aria-labelledby="form-heading" className="space-y-3">
        <h2 id="form-heading" className="text-lg font-semibold">
          Recent form
        </h2>
        <QueryContent
          query={form}
          loading={<TableSkeleton rows={5} />}
          isEmpty={(d) => d.items.length === 0}
          emptyTitle="No matches found"
        >
          {(d) => (
            <Card>
              <CardHeader>
                <CardTitle>Last {d.items.length} matches</CardTitle>
                <CardDescription>
                  <span className="sr-only">Results, newest first: </span>
                  <span className="flex flex-wrap gap-1.5" aria-label="Results, newest first">
                    {d.items.map((m) => {
                      const o = outcome(m, team.id);
                      return (
                        <Badge key={m.id} variant={o.variant} title={m.resultText}>
                          {o.label}
                        </Badge>
                      );
                    })}
                  </span>
                </CardDescription>
              </CardHeader>
              <CardContent>
                <DataTable
                  caption={`${team.name} recent matches`}
                  columns={matchColumns}
                  rows={d.items}
                  rowKey={(m) => m.id}
                  rowHref={(m) => `/matches/${m.id}`}
                  exportFileName={`${team.code} recent matches`}
                />
              </CardContent>
            </Card>
          )}
        </QueryContent>
      </section>

      <ChartCard
        title="Wins batting first and chasing by season"
        description="Click a season to see its matches"
      >
        <QueryContent
          query={seasons}
          loading={<ChartSkeleton height={260} />}
          isEmpty={(d) => d.length === 0}
          emptyTitle={NO_STATS}
        >
          {(rows) => (
            <BarChart
              height={260}
              zoom={rows.length > 12}
              categories={rows.map((r) => r.season)}
              series={[
                { name: 'Won batting first', data: rows.map((r) => r.stats?.battingFirst?.won), stack: 'w' },
                { name: 'Won chasing', data: rows.map((r) => r.stats?.chasing?.won), stack: 'w' },
                { name: 'Lost', data: rows.map((r) => r.stats?.overall?.lost), color: '#dc2626' },
              ]}
              ariaLabel={`${team.name} wins batting first and chasing per season`}
              onItemClick={(_, season) => navigate(`/matches?team=${team.code}&season=${season}`)}
            />
          )}
        </QueryContent>
      </ChartCard>

      <section aria-labelledby="toss-heading" className="space-y-3">
        <h2 id="toss-heading" className="text-lg font-semibold">
          Toss
        </h2>
        <QueryContent
          query={toss}
          loading={<StatCardSkeleton />}
          isEmpty={(d) => (d.team?.matches ?? 0) === 0}
          emptyTitle={NO_STATS}
        >
          {(d) => <TossSection data={d} subject={`${team.code} matches`} />}
        </QueryContent>
      </section>
    </div>
  );
}
