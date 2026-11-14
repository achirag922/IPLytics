import { Trophy } from 'lucide-react';
import { useParams } from 'react-router';
import { ApiError } from '@/api/client';
import type { PointsTableRow } from '@/api/seasonsApi';
import { PlayerLink, TeamLink } from '@/components/cards/EntityLinks';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { PageHeader } from '@/components/layout/PageHeader';
import { PageSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { ErrorState } from '@/components/states/ErrorState';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { usePointsTableQuery, useSeasonSummaryQuery } from '@/hooks/useSeasonQueries';
import { formatDecimal, formatInteger, formatNetRunRate, formatScore } from '@/lib/format';
import { NotFoundPage } from '@/features/notFound/NotFoundPage';

const pointsColumns: Column<PointsTableRow>[] = [
  { key: 'pos', header: '#', align: 'right', cell: (r) => r.position },
  { key: 'team', header: 'Team', cell: (r) => <TeamLink team={r.team} /> },
  { key: 'm', header: 'M', title: 'Matches', align: 'right', cell: (r) => r.matches },
  { key: 'w', header: 'W', title: 'Won', align: 'right', cell: (r) => r.wins },
  { key: 'l', header: 'L', title: 'Lost', align: 'right', cell: (r) => r.losses },
  { key: 'nr', header: 'NR', title: 'No result', align: 'right', cell: (r) => r.noResults },
  {
    key: 'pts',
    header: 'Pts',
    title: 'Points',
    align: 'right',
    cell: (r) => <span className="font-semibold">{r.points}</span>,
  },
  {
    key: 'nrr',
    header: 'NRR',
    title: 'Net run rate',
    align: 'right',
    cell: (r) => formatNetRunRate(r.netRunRate),
  },
  { key: 'for', header: 'For', align: 'right', cell: (r) => `${r.runsFor}/${r.oversFaced}` },
  { key: 'ag', header: 'Against', align: 'right', cell: (r) => `${r.runsAgainst}/${r.oversBowled}` },
];

export function SeasonDetailPage() {
  const yearParam = useParams().year;
  const year = yearParam && /^\d{4}$/.test(yearParam) ? Number(yearParam) : undefined;
  const summary = useSeasonSummaryQuery(year);
  const points = usePointsTableQuery(year);

  if (year === undefined) return <NotFoundPage resource="Season" />;
  if (summary.isPending) return <PageSkeleton label="Loading season" />;
  if (summary.isError) {
    return summary.error instanceof ApiError && ['not-found', 'bad-request'].includes(summary.error.kind) ? (
      <NotFoundPage resource="Season" />
    ) : (
      <ErrorState error={summary.error} onRetry={() => void summary.refetch()} />
    );
  }
  const s = summary.data;
  const hs = s.highestTeamScore;
  const hi = s.highestIndividualScore;

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Seasons', to: '/seasons' }, { label: String(year) }]}
        title={s.name ?? `IPL ${year}`}
        description={
          s.champion ? (
            <span className="flex items-center gap-1.5">
              <Trophy aria-hidden="true" className="size-4 text-amber-500" /> Champion:{' '}
              <TeamLink team={s.champion} />
            </span>
          ) : undefined
        }
      />
      <StatGrid>
        <StatCard label="Matches" value={formatInteger(s.matches)} />
        <StatCard
          label="Runs"
          value={formatInteger(s.totalRuns)}
          hint={`${formatInteger(s.fours)} fours, ${formatInteger(s.sixes)} sixes`}
        />
        <StatCard label="Wickets" value={formatInteger(s.wickets)} />
        <StatCard
          label="Average score"
          value={formatDecimal(s.averageScore)}
          hint={`Run rate ${formatDecimal(s.runRate)}`}
        />
        <StatCard
          label="Highest team score"
          value={hs ? `${hs.runs}/${hs.wickets}` : '–'}
          hint={
            hs && (
              <>
                <TeamLink team={hs.team} /> v {hs.opponent?.code}
              </>
            )
          }
        />
        <StatCard
          label="Highest individual score"
          value={hi ? `${formatScore(hi.runs, hi.notOut)} (${hi.balls})` : '–'}
          hint={
            hi && (
              <>
                <PlayerLink player={hi.player} /> v {hi.opponent?.code}
              </>
            )
          }
        />
        <StatCard
          label="Top run scorer"
          value={formatInteger(s.topRunScorer?.value)}
          hint={<PlayerLink player={s.topRunScorer?.player} />}
        />
        <StatCard
          label="Top wicket taker"
          value={formatInteger(s.topWicketTaker?.value)}
          hint={<PlayerLink player={s.topWicketTaker?.player} />}
        />
      </StatGrid>
      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Points table</CardTitle>
          <CardDescription>
            League stage. 2 points a win, 1 for a no result; ties decided by a super over count as wins. NRR =
            runs per over scored − runs per over conceded (an all-out side is charged its full overs).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <QueryContent
            query={points}
            loading={<TableSkeleton columns={10} />}
            isEmpty={(d) => (d.rows ?? []).length === 0}
            emptyTitle="No league matches this season"
          >
            {(d) => (
              <DataTable
                caption={`IPL ${year} points table`}
                columns={pointsColumns}
                rows={d.rows ?? []}
                rowKey={(r) => r.team?.id ?? r.position}
              />
            )}
          </QueryContent>
        </CardContent>
      </Card>
    </>
  );
}
