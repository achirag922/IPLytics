import { Outlet } from 'react-router';
import type { PlayerContext } from '@/features/detailContext';
import { ApiError } from '@/api/client';
import { TeamLink } from '@/components/cards/EntityLinks';
import { StatsFilterBar } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { TabNav } from '@/components/layout/TabNav';
import { PageSkeleton } from '@/components/loading/Skeletons';
import { ErrorState } from '@/components/states/ErrorState';
import { useNumericParam } from '@/hooks/useNumericParam';
import { usePlayerQuery } from '@/hooks/usePlayerQueries';
import { useStatsFilters } from '@/hooks/useStatsFilters';
import { NotFoundPage } from '@/features/notFound/NotFoundPage';

const TABS = [
  { label: 'Overview', to: '' },
  { label: 'Batting', to: 'batting' },
  { label: 'Bowling', to: 'bowling' },
  { label: 'Phases', to: 'phases' },
  { label: 'Vs Teams', to: 'vs-teams' },
  { label: 'Venues', to: 'venues' },
];

export function PlayerDetailPage() {
  const playerId = useNumericParam('playerId');
  const player = usePlayerQuery(playerId);
  const filters = useStatsFilters();

  if (playerId === undefined) return <NotFoundPage resource="Player" />;
  if (player.isPending) return <PageSkeleton label="Loading player" />;
  if (player.isError) {
    return player.error instanceof ApiError && player.error.kind === 'not-found' ? (
      <NotFoundPage resource="Player" />
    ) : (
      <ErrorState error={player.error} onRetry={() => void player.refetch()} />
    );
  }

  const p = player.data;
  const spells = p.teams ?? [];
  // The franchise of the most recent spell (the API lists spells oldest first).
  const latest = spells[spells.length - 1];
  const otherNames = (p.aliases ?? []).filter((a) => a.name !== p.name);
  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Players', to: '/players' }, { label: p.name ?? '' }]}
        title={p.name}
        description={
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            {latest && (
              <span>
                <TeamLink team={latest.team} /> ({latest.lastSeason})
              </span>
            )}
            <span>· {p.matches} matches</span>
            <span>
              · {p.firstSeason}–{p.lastSeason}
            </span>
            {otherNames.length > 0 && (
              <span>· Also listed as {otherNames.map((a) => a.name).join(', ')}</span>
            )}
          </span>
        }
      />
      <TabNav label="Player sections" tabs={TABS} />
      <StatsFilterBar state={filters} show={{ opponent: true, venue: true }} />
      <Outlet context={{ player: p, query: filters.query } satisfies PlayerContext} />
    </>
  );
}
