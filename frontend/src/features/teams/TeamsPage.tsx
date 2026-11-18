import { Link } from 'react-router';
import { CardGrid, TeamCard } from '@/components/cards/EntityCards';
import { TeamLogo } from '@/components/media/TeamLogo';
import { Skeleton } from '@/components/ui/skeleton';
import type { Team } from '@/api/teamsApi';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { PageHeader } from '@/components/layout/PageHeader';
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
        <TeamLogo team={t} size="xs" />
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

function TeamGroup({ title, teams }: { title: string; teams: Team[] }) {
  if (teams.length === 0) return null;
  return (
    <section aria-label={title} className="space-y-3">
      <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
      <CardGrid>
        {teams.map((t) => (
          <TeamCard key={t.id} team={t} />
        ))}
      </CardGrid>
    </section>
  );
}

export function TeamsPage() {
  const teams = useTeamsQuery();
  return (
    <>
      <PageHeader
        title="Teams"
        description="Every IPL franchise. Renamed teams are one franchise (e.g. Delhi Daredevils → Delhi Capitals)."
      />
      <QueryContent
        query={teams}
        loading={
          <CardGrid>
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} className="h-48 rounded-xl" />
            ))}
          </CardGrid>
        }
        isEmpty={(d) => d.length === 0}
      >
        {(data) => {
          const sorted = [...data].sort(byActivityThenName);
          return (
            <div className="space-y-8">
              <TeamGroup title="Current franchises" teams={sorted.filter((t) => t.active)} />
              <TeamGroup title="Former franchises" teams={sorted.filter((t) => !t.active)} />
            </div>
          );
        }}
      </QueryContent>
      {teams.data && teams.data.length > 0 && (
        <Card className="mt-8">
          <CardHeader>
            <CardTitle>All-time records</CardTitle>
          </CardHeader>
          <CardContent>
            <DataTable
              caption="IPL franchises with all-time record and latest season"
              columns={columns}
              rows={[...teams.data].sort(byActivityThenName)}
              rowKey={(t) => t.id}
              rowHref={(t) => `/teams/${t.id}`}
            />
          </CardContent>
        </Card>
      )}
    </>
  );
}
