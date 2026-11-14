import { useMemo } from 'react';
import type { Venue } from '@/api/venuesApi';
import { VenueLink } from '@/components/cards/EntityLinks';
import { FilterBar } from '@/components/filters';
import { SearchFilter } from '@/components/filters/SearchFilter';
import { PageHeader } from '@/components/layout/PageHeader';
import { TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { Card, CardContent } from '@/components/ui/card';
import { useUrlState } from '@/hooks/useUrlState';
import { useVenuesQuery } from '@/hooks/useVenueQueries';
import { formatInteger } from '@/lib/format';

const columns: Column<Venue>[] = [
  { key: 'venue', header: 'Venue', cell: (v) => <VenueLink venue={v} /> },
  { key: 'city', header: 'City', cell: (v) => v.city ?? '–' },
  { key: 'm', header: 'Matches', align: 'right', cell: (v) => formatInteger(v.matches) },
  {
    key: 'span',
    header: 'Seasons',
    cell: (v) => (v.firstSeason === v.lastSeason ? v.firstSeason : `${v.firstSeason}–${v.lastSeason}`),
  },
];

export function VenuesPage() {
  const url = useUrlState();
  const search = url.get('search');
  const venues = useVenuesQuery();
  // The venue list is small (one request, cached), so the name filter runs in the browser.
  const filter = useMemo(() => {
    const term = (search ?? '').toLowerCase();
    return (v: Venue) =>
      !term || [v.name, v.city, ...(v.aliases ?? [])].some((text) => text?.toLowerCase().includes(term));
  }, [search]);

  return (
    <>
      <PageHeader title="Venues" description="Grounds that have hosted IPL matches, by number of matches." />
      <FilterBar onReset={() => url.update({ search: undefined })} resetDisabled={!search}>
        <SearchFilter
          label="Search"
          placeholder="Venue or city"
          value={search}
          onChange={(v) => url.update({ search: v })}
        />
      </FilterBar>
      <Card>
        <CardContent className="pt-4 sm:pt-5">
          <QueryContent
            query={venues}
            loading={<TableSkeleton columns={4} />}
            isEmpty={(d) => d.length === 0}
          >
            {(data) => (
              <DataTable
                caption="IPL venues"
                columns={columns}
                rows={data.filter(filter)}
                rowKey={(v) => v.id}
                rowHref={(v) => `/venues/${v.id}`}
                emptyTitle="No venues found"
              />
            )}
          </QueryContent>
        </CardContent>
      </Card>
    </>
  );
}
