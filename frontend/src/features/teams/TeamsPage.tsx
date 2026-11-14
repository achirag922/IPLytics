import { Link } from 'react-router';
import type { Team } from '@/api/teamsApi';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { PageHeader } from '@/components/layout/PageHeader';
import { TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { useTeamsQuery } from '@/hooks/useTeamQueries';
import { formatInteger, formatPercent } from '@/lib/format';

const columns: Column<Team>[] = [
  {
    key: 'team',
    header: 'Team',
    cell: (t) => (
      <div className="flex items-center gap-2">
        <Link to={`/teams/${t.id}`} className="font-medium text-primary hover:underline">
          {t.name}
        </Link>
        {!t.active && <Badge variant="outline">Defunct</Badge>}
      </div>
    ),
  },
  { key: 'code', header: 'Code', cell: (t) => t.code },
  {
    key: 'matches',
    header: 'M',
    title: 'Matches',
    align: 'right',
    cell: (t) => formatInteger(t.record?.played),
  },
  { key: 'won', header: 'W', title: 'Wins', align: 'right', cell: (t) => formatInteger(t.record?.won) },
  { key: 'lost', header: 'L', title: 'Losses', align: 'right', cell: (t) => formatInteger(t.record?.lost) },
  {
    key: 'nr',
    header: 'NR',
    title: 'No result',
    align: 'right',
    cell: (t) => formatInteger(t.record?.noResult),
  },
  { key: 'pct', header: 'Win %', align: 'right', cell: (t) => formatPercent(t.record?.winPercentage) },
  {
    key: 'latest',
    header: 'Latest season',
    cell: (t) =>
      t.latestSeason ? (
        <span>
          {t.latestSeason.season}: {t.latestSeason.record?.won ?? 0} won, {t.latestSeason.record?.lost ?? 0}{' '}
          lost
        </span>
      ) : (
        '–'
      ),
  },
  {
    key: 'span',
    header: 'Seasons',
    cell: (t) => (t.firstSeason ? `${t.firstSeason}–${t.active ? 'present' : t.lastSeason}` : '–'),
  },
];

const byActivityThenName = (a: Team, b: Team) =>
  Number(b.active) - Number(a.active) || (a.name ?? '').localeCompare(b.name ?? '');

export function TeamsPage() {
  const teams = useTeamsQuery();
  return (
    <>
      <PageHeader
        title="Teams"
        description="Every IPL franchise. Renamed teams are one franchise (e.g. Delhi Daredevils → Delhi Capitals)."
      />
      <Card>
        <CardContent className="pt-4 sm:pt-5">
          <QueryContent query={teams} loading={<TableSkeleton columns={8} />} isEmpty={(d) => d.length === 0}>
            {(data) => (
              <DataTable
                caption="IPL franchises with all-time record and latest season"
                columns={columns}
                rows={[...data].sort(byActivityThenName)}
                rowKey={(t) => t.id}
                rowHref={(t) => `/teams/${t.id}`}
              />
            )}
          </QueryContent>
        </CardContent>
      </Card>
    </>
  );
}
