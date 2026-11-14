import { Outlet } from 'react-router';
import type { TeamContext } from '@/features/detailContext';
import { ApiError } from '@/api/client';
import { Badge } from '@/components/ui/badge';
import { StatsFilterBar } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { TabNav } from '@/components/layout/TabNav';
import { PageSkeleton } from '@/components/loading/Skeletons';
import { ErrorState } from '@/components/states/ErrorState';
import { useNumericParam } from '@/hooks/useNumericParam';
import { useStatsFilters } from '@/hooks/useStatsFilters';
import { useTeamQuery } from '@/hooks/useTeamQueries';
import { NotFoundPage } from '@/features/notFound/NotFoundPage';

const TABS = [
  { label: 'Overview', to: '' },
  { label: 'Seasons', to: 'seasons' },
  { label: 'Batting', to: 'batting' },
  { label: 'Bowling', to: 'bowling' },
  { label: 'Venues', to: 'venues' },
  { label: 'Head-to-Head', to: 'head-to-head' },
];

export function TeamDetailPage() {
  const teamId = useNumericParam('teamId');
  const team = useTeamQuery(teamId);
  const filters = useStatsFilters();

  if (teamId === undefined) return <NotFoundPage resource="Team" />;
  if (team.isPending) return <PageSkeleton label="Loading team" />;
  if (team.isError) {
    return team.error instanceof ApiError && team.error.kind === 'not-found' ? (
      <NotFoundPage resource="Team" />
    ) : (
      <ErrorState error={team.error} onRetry={() => void team.refetch()} />
    );
  }

  const t = team.data;
  const formerNames = (t.names ?? []).filter((n) => n.name !== t.name);
  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Teams', to: '/teams' }, { label: t.name ?? '' }]}
        title={
          <span className="flex flex-wrap items-center gap-3">
            {t.name}
            <Badge variant="outline">{t.code}</Badge>
            {!t.active && <Badge variant="destructive">Defunct</Badge>}
          </span>
        }
        description={
          <>
            {t.homeCity && <>Home: {t.homeCity} · </>}
            {t.firstSeason}–{t.active ? 'present' : t.lastSeason}
            {formerNames.length > 0 && <> · Also played as {formerNames.map((n) => n.name).join(', ')}</>}
          </>
        }
      />
      <TabNav label="Team sections" tabs={TABS} />
      <StatsFilterBar
        state={filters}
        show={{ opponent: true, venue: true }}
        excludeOpponents={t.code ? [t.code] : []}
      />
      <Outlet context={{ team: t, query: filters.query } satisfies TeamContext} />
    </>
  );
}
