import { useMemo } from 'react';
import type { PlayerListItem, PlayerListQuery } from '@/api/playersApi';
import { PlayerLink, TeamLink } from '@/components/cards/EntityLinks';
import { FilterBar, SeasonFilter, SeasonRangeFilter, SelectFilter, TeamFilter } from '@/components/filters';
import { SearchFilter } from '@/components/filters/SearchFilter';
import { PageHeader } from '@/components/layout/PageHeader';
import { TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { PaginationControls } from '@/components/tables/PaginationControls';
import { Card, CardContent } from '@/components/ui/card';
import { usePlayersQuery } from '@/hooks/usePlayerQueries';
import { useStatsFilters } from '@/hooks/useStatsFilters';
import { formatDecimal, formatInteger } from '@/lib/format';

const PAGE_SIZE = 25;
const DISCIPLINES = [
  { value: 'batting', label: 'Batters' },
  { value: 'bowling', label: 'Bowlers' },
];
const SORTS = [
  { value: 'name', label: 'Name' },
  { value: 'matches', label: 'Most matches' },
  { value: 'runs', label: 'Most runs' },
  { value: 'wickets', label: 'Most wickets' },
];
type Sort = NonNullable<PlayerListQuery['sort']>;
type Discipline = NonNullable<PlayerListQuery['discipline']>;
const isSort = (v: string | undefined): v is Sort => SORTS.some((s) => s.value === v);
const isDiscipline = (v: string | undefined): v is Discipline => DISCIPLINES.some((d) => d.value === v);

const columns: Column<PlayerListItem>[] = [
  { key: 'player', header: 'Player', cell: (p) => <PlayerLink player={p} /> },
  { key: 'team', header: 'Team', cell: (p) => <TeamLink team={p.team} short /> },
  {
    key: 'span',
    header: 'Seasons',
    cell: (p) => (p.firstSeason === p.lastSeason ? p.firstSeason : `${p.firstSeason}–${p.lastSeason}`),
  },
  { key: 'm', header: 'M', title: 'Matches', align: 'right', cell: (p) => formatInteger(p.matches) },
  { key: 'runs', header: 'Runs', align: 'right', cell: (p) => formatInteger(p.runs) },
  {
    key: 'avg',
    header: 'Avg',
    title: 'Batting average',
    align: 'right',
    cell: (p) => formatDecimal(p.battingAverage),
  },
  { key: 'sr', header: 'SR', title: 'Strike rate', align: 'right', cell: (p) => formatDecimal(p.strikeRate) },
  { key: 'wkts', header: 'Wkts', title: 'Wickets', align: 'right', cell: (p) => formatInteger(p.wickets) },
  { key: 'econ', header: 'Econ', title: 'Economy', align: 'right', cell: (p) => formatDecimal(p.economy) },
];

export function PlayersPage() {
  const state = useStatsFilters();
  const { filters, url } = state;
  const search = url.get('search');
  const discipline = url.get('discipline');
  const sort = url.get('sort');
  const page = url.getNumber('page') ?? 0;

  const query = useMemo<PlayerListQuery | undefined>(
    () =>
      state.query && {
        search,
        season: state.query.season,
        seasonFrom: state.query.seasonFrom,
        seasonTo: state.query.seasonTo,
        team: state.query.team,
        discipline: isDiscipline(discipline) ? discipline : undefined,
        sort: isSort(sort) ? sort : undefined,
        page,
        size: PAGE_SIZE,
      },
    [state.query, search, discipline, sort, page],
  );
  const players = usePlayersQuery(query);
  const anyFilter = ['search', 'season', 'seasonFrom', 'seasonTo', 'team', 'discipline', 'sort'].some((k) =>
    url.get(k),
  );

  return (
    <>
      <PageHeader
        title="Players"
        description="Everyone who played in an IPL XI. Figures cover the selected seasons and franchise."
      />
      <FilterBar
        onReset={() =>
          url.update({
            search: undefined,
            season: undefined,
            seasonFrom: undefined,
            seasonTo: undefined,
            team: undefined,
            discipline: undefined,
            sort: undefined,
          })
        }
        resetDisabled={!anyFilter}
      >
        <SearchFilter
          label="Search"
          placeholder="Player name"
          value={search}
          onChange={(v) => url.update({ search: v })}
        />
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
        <SelectFilter
          label="Discipline"
          value={discipline}
          options={DISCIPLINES}
          allLabel="All players"
          onChange={(v) => url.update({ discipline: v })}
        />
        <SelectFilter
          label="Sort by"
          value={isSort(sort) ? sort : 'name'}
          options={SORTS}
          onChange={(v) => url.update({ sort: v === 'name' ? undefined : v })}
        />
      </FilterBar>
      <Card>
        <CardContent className="pt-4 sm:pt-5">
          <QueryContent
            query={players}
            loading={<TableSkeleton rows={10} columns={9} />}
            isEmpty={(d) => d.items.length === 0}
            emptyTitle="No players found"
            emptyDescription="Try a different name or widen the filters."
          >
            {(d) => (
              <>
                <DataTable
                  caption="Players matching the filters"
                  columns={columns}
                  rows={d.items}
                  rowKey={(p) => p.id}
                  rowHref={(p) => `/players/${p.id}`}
                />
                <PaginationControls
                  pagination={d.pagination}
                  isFetching={players.isFetching}
                  itemLabel="players"
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
