import { Outlet } from 'react-router';
import { StatsFilterBar } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { TabNav } from '@/components/layout/TabNav';
import type { InsightsContext } from '@/features/detailContext';
import { useStatsFilters } from '@/hooks/useStatsFilters';

const TABS = [
  { label: 'Toss impact', to: '' },
  { label: 'Batting first vs chasing', to: 'chasing' },
  { label: 'Phases', to: 'phases' },
  { label: 'Season comparison', to: 'seasons' },
];

/** League-wide insights; the filters (seasons, team, opponent, venue) apply to every tab and live in the URL. */
export function InsightsPage() {
  const filters = useStatsFilters();
  return (
    <>
      <PageHeader
        title="Insights"
        description="How the toss, batting order and phases of the innings shape IPL matches. Click a season in a chart to focus on it."
      />
      <TabNav label="Insight sections" tabs={TABS} />
      <StatsFilterBar state={filters} show={{ team: true, opponent: true, venue: true }} />
      <Outlet
        context={
          { query: filters.query, filters: filters.filters, url: filters.url } satisfies InsightsContext
        }
      />
    </>
  );
}
