import { useMemo } from 'react';

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

  const query = useMemo<MatchListQuery | undefined>(
    () => state.query && { ...state.query, dateFrom, dateTo, stage, page, size: PAGE_SIZE },
    [state.query, dateFrom, dateTo, stage, page],
  );
  const matches = useMatchesQuery(query);

  return (
    <>
      <PageHeader title="Matches" description="Every IPL result, newest first." />
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
      <Card>
        <CardContent className="pt-4 sm:pt-5">
          <QueryContent
            query={matches}
            loading={<TableSkeleton rows={10} columns={7} />}
            isEmpty={(d) => d.items.length === 0}
            emptyTitle="No matches found"
            emptyDescription="No match fits all of the selected filters."
          >
            {(d) => (
              <>
                <DataTable
                  caption="Matches matching the filters"
                  columns={matchColumns}
                  rows={d.items}
                  rowKey={(m) => m.id}
                  rowHref={(m) => `/matches/${m.id}`}
                />
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
