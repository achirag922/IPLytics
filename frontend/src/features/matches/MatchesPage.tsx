import { LayoutGrid, Rows3 } from 'lucide-react';
import { useMemo } from 'react';
import { CardGrid, MatchCard } from '@/components/cards/EntityCards';
import { CardGridSkeleton } from '@/components/loading/Skeletons';
import { SegmentedControl } from '@/components/ui/segmented';

import type { MatchListQuery } from '@/api/matchesApi';
import { matchColumns } from './matchColumns';
import {
  DateRangeFilter,
  FilterBar,
  SeasonFilter,
  SeasonRangeFilter,
  SelectFilter,
  TeamFilter,
  VenueFilter,
} from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { PaginationControls } from '@/components/tables/PaginationControls';

import { Card, CardContent } from '@/components/ui/card';
import { useMatchesQuery } from '@/hooks/useMatchQueries';
import { matchesApi } from '@/api/matchesApi';
import { ExportAllButton } from '@/components/tables/ExportAllButton';
import { MATCH_CSV_COLUMNS } from '@/features/exportColumns';
import { useExportAll } from '@/hooks/useExportAll';
import { csvFileName } from '@/lib/csv';
import { queryKeys } from '@/lib/queryKeys';
import { useStatsFilters } from '@/hooks/useStatsFilters';

const PAGE_SIZE = 25;
const STAGES = [
  'League',
  'Qualifier 1',
  'Eliminator',
  'Qualifier 2',
  'Semi Final',
  '3rd Place Play-Off',
  'Final',
].map((s) => ({
  value: s,
  label: s,
}));
const FILTER_KEYS = [
  'season',
  'seasonFrom',
  'seasonTo',
  'team',
  'opponent',
  'venue',
  'dateFrom',
  'dateTo',
  'stage',
];

export function MatchesPage() {
  const state = useStatsFilters();
  const { filters, url } = state;
  const dateFrom = url.get('dateFrom');
  const dateTo = url.get('dateTo');
  const stage = url.get('stage');
  const page = url.getNumber('page') ?? 0;
  const view = url.get('view') === 'table' ? 'table' : 'cards';

  const query = useMemo<MatchListQuery | undefined>(
    () => state.query && { ...state.query, dateFrom, dateTo, stage, page, size: PAGE_SIZE },
    [state.query, dateFrom, dateTo, stage, page],
  );
  const matches = useMatchesQuery(query);
  const exporter = useExportAll({
    queryKey: queryKeys.matches.list({ ...query, page: undefined, size: undefined }),
    fetchPage: (page, size, signal) => matchesApi.list({ ...query, page, size }, signal),
    columns: MATCH_CSV_COLUMNS,
    fileName: csvFileName('matches', {
      season: filters.season,
      team: filters.team,
      opponent: filters.opponent,
      stage,
    }),
  });

  return (
    <>
      <PageHeader
        title="Matches"
        description="Every IPL result, newest first."
        actions={
          <SegmentedControl
            label="View"
            value={view}
            options={[
              { value: 'cards', label: 'Cards', icon: LayoutGrid },
              { value: 'table', label: 'Table', icon: Rows3 },
            ]}
            onChange={(v) => url.update({ view: v === 'cards' ? undefined : v })}
          />
        }
      />
      <FilterBar
        onReset={() => url.update(Object.fromEntries(FILTER_KEYS.map((k) => [k, undefined])))}
        resetDisabled={!FILTER_KEYS.some((k) => url.get(k))}
      >
        <SeasonFilter
          value={filters.season}
          onChange={(season) => url.update({ season, seasonFrom: undefined, seasonTo: undefined })}
        />
        <SeasonRangeFilter
          from={filters.season === undefined ? filters.seasonFrom : undefined}
          to={filters.season === undefined ? filters.seasonTo : undefined}
          onChange={({ from, to }) => url.update({ season: undefined, seasonFrom: from, seasonTo: to })}
        />
        <TeamFilter value={filters.team} onChange={(team) => url.update({ team })} />
        <TeamFilter
          label="Opponent"
          allLabel="Any opponent"
          value={filters.opponent}
          exclude={filters.team ? [filters.team] : []}
          onChange={(opponent) => url.update({ opponent })}
        />
        <VenueFilter value={filters.venue} onChange={(venue) => url.update({ venue })} />
        <DateRangeFilter
          from={dateFrom}
          to={dateTo}
          onChange={({ from, to }) => url.update({ dateFrom: from, dateTo: to })}
        />
        <SelectFilter
          label="Stage"
          value={stage}
          options={STAGES}
          allLabel="All stages"
          onChange={(v) => url.update({ stage: v })}
        />
      </FilterBar>
      <Card className={view === 'cards' ? 'border-0 bg-transparent shadow-none' : undefined}>
        <CardContent className={view === 'cards' ? 'p-0 sm:p-0' : 'pt-4 sm:pt-5'}>
          <QueryContent
            query={matches}
            loading={view === 'cards' ? <CardGridSkeleton /> : <TableSkeleton rows={10} columns={7} />}
            isEmpty={(d) => d.items.length === 0}
            emptyTitle="No matches found"
            emptyDescription="No match fits all of the selected filters."
          >
            {(d) => (
              <>
                {view === 'cards' ? (
                  <section aria-label="Matches matching the filters" className="space-y-3">
                    <div className="flex justify-end">
                      <ExportAllButton exporter={exporter} total={d.pagination.totalElements} />
                    </div>
                    <CardGrid className="xl:grid-cols-3">
                      {d.items.map((m) => (
                        <MatchCard key={m.id} match={m} />
                      ))}
                    </CardGrid>
                  </section>
                ) : (
                  <DataTable
                    caption="Matches matching the filters"
                    columns={matchColumns}
                    rows={d.items}
                    rowKey={(m) => m.id}
                    rowHref={(m) => `/matches/${m.id}`}
                    exportFileName="matches page"
                    toolbar={<ExportAllButton exporter={exporter} total={d.pagination.totalElements} />}
                  />
                )}
                <PaginationControls
                  pagination={d.pagination}
                  isFetching={matches.isFetching}
                  itemLabel="matches"
                  onPageChange={(next) => url.update({ page: next === 0 ? undefined : next })}
                />
              </>
            )}
          </QueryContent>
        </CardContent>
      </Card>
    </>
  );
}
