import { ApiError } from '@/api/client';
import type { ScoreBand } from '@/api/venuesApi';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { BarChart, ChartCard, LineChart, PieChart } from '@/components/charts';
import { StatsFilterBar } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { ChartSkeleton, PageSkeleton, StatCardSkeleton } from '@/components/loading/Skeletons';
import { ErrorState } from '@/components/states/ErrorState';
import { QueryContent } from '@/components/states/QueryContent';
import { useNumericParam } from '@/hooks/useNumericParam';
import { useStatsFilters } from '@/hooks/useStatsFilters';
import {
  useVenueQuery,
  useVenueScoreDistributionQuery,
  useVenueSeasonsQuery,
  useVenueStatsQuery,
} from '@/hooks/useVenueQueries';
import { formatDecimal, formatInteger, formatPercent } from '@/lib/format';
import { NotFoundPage } from '@/features/notFound/NotFoundPage';

const NO_STATS = 'No statistics available for the selected filters';

function bandLabel(band: ScoreBand): string {
  if (band.from === undefined) return `< ${(band.to ?? 0) + 1}`;
  if (band.to === undefined) return `${band.from}+`;
  return `${band.from}–${band.to}`;
}

export function VenueDetailPage() {
  const venueId = useNumericParam('venueId');
  const venue = useVenueQuery(venueId);
  const filters = useStatsFilters();
  const seasonQuery = {
    season: filters.query?.season,
    seasonFrom: filters.query?.seasonFrom,
    seasonTo: filters.query?.seasonTo,
  };
  const stats = useVenueStatsQuery(venueId, seasonQuery);
  const seasons = useVenueSeasonsQuery(venueId, seasonQuery);
  const distribution = useVenueScoreDistributionQuery(venueId, seasonQuery);

  if (venueId === undefined) return <NotFoundPage resource="Venue" />;
  if (venue.isPending) return <PageSkeleton label="Loading venue" />;
  if (venue.isError) {
    return venue.error instanceof ApiError && venue.error.kind === 'not-found' ? (
      <NotFoundPage resource="Venue" />
    ) : (
      <ErrorState error={venue.error} onRetry={() => void venue.refetch()} />
    );
  }
  const v = venue.data;

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Venues', to: '/venues' }, { label: v.name ?? '' }]}
        title={v.name}
        description={`${v.city ?? ''} · ${v.matches} matches · ${v.firstSeason}–${v.lastSeason}`}
      />
      <StatsFilterBar state={filters} />
      <QueryContent
        query={stats}
        loading={<StatCardSkeleton count={8} />}
        isEmpty={(s) => s.matches === 0}
        emptyTitle={NO_STATS}
      >
        {(s) => (
          <div className="space-y-4">
            <StatGrid>
              <StatCard
                label="Matches"
                value={formatInteger(s.matches)}
                hint={`${formatInteger(s.decisiveMatches)} with a result`}
              />
              <StatCard label="Avg 1st-innings score" value={formatDecimal(s.averageFirstInningsScore)} />
              <StatCard label="Highest score" value={formatInteger(s.highestScore)} />
              <StatCard
                label="Lowest score"
                value={formatInteger(s.lowestScore)}
                hint="All out or full overs"
              />
              <StatCard
                label="Batting first win %"
                value={formatPercent(s.battingFirstWinPercentage)}
                hint={`${formatInteger(s.battingFirstWins)} wins`}
              />
              <StatCard
                label="Chasing win %"
                value={formatPercent(s.chasingWinPercentage)}
                hint={`${formatInteger(s.chasingWins)} wins`}
              />
              <StatCard label="Average run rate" value={formatDecimal(s.averageRunRate)} />
              <StatCard
                label="Per innings"
                value={`${formatDecimal(s.wicketsPerInnings)} wkts`}
                hint={`${formatDecimal(s.boundariesPerInnings)} boundaries`}
              />
            </StatGrid>
            <div className="grid gap-4 lg:grid-cols-3">
              <ChartCard title="Batting first vs chasing" description="Matches with a result">
                <PieChart
                  height={260}
                  data={[
                    { name: 'Won batting first', value: s.battingFirstWins },
                    { name: 'Won chasing', value: s.chasingWins },
                  ]}
                  ariaLabel={`Matches won batting first and chasing at ${v.name}`}
                />
              </ChartCard>
              <ChartCard title="First-innings score distribution" className="lg:col-span-2">
                <QueryContent
                  query={distribution}
                  loading={<ChartSkeleton height={260} />}
                  isEmpty={(d) => d.every((b) => b.innings === 0)}
                  emptyTitle={NO_STATS}
                >
                  {(bands) => (
                    <BarChart
                      height={260}
                      categories={bands.map(bandLabel)}
                      series={[{ name: 'Innings', data: bands.map((b) => b.innings) }]}
                      xAxisName="First-innings total"
                      ariaLabel={`Distribution of first-innings totals at ${v.name}`}
                    />
                  )}
                </QueryContent>
              </ChartCard>
            </div>
          </div>
        )}
      </QueryContent>
      <ChartCard title="Scores by season" className="mt-4">
        <QueryContent
          query={seasons}
          loading={<ChartSkeleton height={280} />}
          isEmpty={(d) => d.length === 0}
          emptyTitle={NO_STATS}
        >
          {(rows) => (
            <LineChart
              height={280}
              categories={rows.map((r) => r.season)}
              series={[
                { name: 'Avg 1st-innings score', data: rows.map((r) => r.stats?.averageFirstInningsScore) },
                { name: 'Highest score', data: rows.map((r) => r.stats?.highestScore) },
              ]}
              ariaLabel={`Average first-innings and highest scores per season at ${v.name}`}
            />
          )}
        </QueryContent>
      </ChartCard>
    </>
  );
}
