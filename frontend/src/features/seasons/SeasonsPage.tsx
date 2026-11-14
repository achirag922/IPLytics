import { Link } from 'react-router';
import type { Season } from '@/api/seasonsApi';
import { TeamLink } from '@/components/cards/EntityLinks';
import { PageHeader } from '@/components/layout/PageHeader';
import { TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { Card, CardContent } from '@/components/ui/card';
import { useSeasonsQuery } from '@/hooks/useSeasonQueries';
import { formatDecimal, formatInteger } from '@/lib/format';

const columns: Column<Season>[] = [
  {
    key: 'year',
    header: 'Season',
    cell: (s) => (
      <Link to={`/seasons/${s.year}`} className="font-medium text-primary hover:underline">
        {s.name}
      </Link>
    ),
  },
  { key: 'champion', header: 'Champion', cell: (s) => <TeamLink team={s.champion} /> },
  { key: 'm', header: 'Matches', align: 'right', cell: (s) => formatInteger(s.matches) },
  { key: 'r', header: 'Runs', align: 'right', cell: (s) => formatInteger(s.runs) },
  { key: 'w', header: 'Wickets', align: 'right', cell: (s) => formatInteger(s.wickets) },
  { key: '6', header: 'Sixes', align: 'right', cell: (s) => formatInteger(s.sixes) },
  { key: 'avg', header: 'Avg score', align: 'right', cell: (s) => formatDecimal(s.averageScore) },
  { key: 'rr', header: 'Run rate', align: 'right', cell: (s) => formatDecimal(s.runRate) },
];

export function SeasonsPage() {
  const seasons = useSeasonsQuery();
  return (
    <>
      <PageHeader title="Seasons" description="Every IPL season with its champion and totals." />
      <Card>
        <CardContent className="pt-4 sm:pt-5">
          <QueryContent
            query={seasons}
            loading={<TableSkeleton columns={8} />}
            isEmpty={(d) => d.length === 0}
          >
            {(data) => (
              <DataTable
                caption="IPL seasons"
                columns={columns}
                rows={[...data].sort((a, b) => b.year - a.year)}
                rowKey={(s) => s.year}
                rowHref={(s) => `/seasons/${s.year}`}
              />
            )}
          </QueryContent>
        </CardContent>
      </Card>
    </>
  );
}
