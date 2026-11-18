import { Outlet } from 'react-router';
import type { PlayerContext } from '@/features/detailContext';
import { ApiError } from '@/api/client';
import { TeamLink } from '@/components/cards/EntityLinks';
import { StatsFilterBar } from '@/components/filters';
import { EntityHero } from '@/components/layout/EntityHero';
import { PlayerAvatar } from '@/components/media/PlayerAvatar';
import { TeamLogo } from '@/components/media/TeamLogo';
import { Badge } from '@/components/ui/badge';
import { teamBranding } from '@/config/media/teamBranding';
import { formatDecimal, formatInteger, formatScore } from '@/lib/format';
import { TabNav } from '@/components/layout/TabNav';
import { PageSkeleton } from '@/components/loading/Skeletons';
import { ErrorState } from '@/components/states/ErrorState';
import { useNumericParam } from '@/hooks/useNumericParam';
import { usePlayerBattingQuery, usePlayerBowlingQuery, usePlayerQuery } from '@/hooks/usePlayerQueries';
import { useStatsFilters } from '@/hooks/useStatsFilters';
import { NotFoundPage } from '@/features/notFound/NotFoundPage';

const TABS = [
  { label: 'Overview', to: '' },
  { label: 'Batting', to: 'batting' },
  { label: 'Bowling', to: 'bowling' },
  { label: 'Phases', to: 'phases' },
  { label: 'Vs Teams', to: 'vs-teams' },
  { label: 'Venues', to: 'venues' },
  { label: 'Consistency', to: 'consistency' },
];

export function PlayerDetailPage() {
  const playerId = useNumericParam('playerId');
  const player = usePlayerQuery(playerId);
  const filters = useStatsFilters();
  // The same queries as the overview tab, so the headline figures share its cache and its filters.
  const batting = usePlayerBattingQuery(playerId, filters.query);
  const bowling = usePlayerBowlingQuery(playerId, filters.query);

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
  const bat = batting.data?.summary;
  const bowl = bowling.data?.summary;
  return (
    <>
      <EntityHero
        breadcrumbs={[{ label: 'Players', to: '/players' }, { label: p.name ?? '' }]}
        accent={teamBranding(latest?.team?.code, p.name)}
        media={<PlayerAvatar player={p} teamCode={latest?.team?.code} size="xl" />}
        eyebrow={
          <>
            {p.firstSeason}–{p.lastSeason} · {p.matches} matches
          </>
        }
        title={p.name}
        subtitle={
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            {latest && (
              <span className="flex items-center gap-1.5">
                <TeamLogo team={latest.team} size="xs" />
                <TeamLink team={latest.team} /> ({latest.lastSeason})
              </span>
            )}
            {otherNames.length > 0 && (
              <span>· Also listed as {otherNames.map((a) => a.name).join(', ')}</span>
            )}
          </span>
        }
        badges={spells.length > 1 ? <Badge variant="outline">{spells.length} franchises</Badge> : undefined}
        stats={[
          {
            label: 'Runs',
            value: formatInteger(bat?.runs),
            hint: bat ? `${bat.innings} innings` : undefined,
          },
          { label: 'Bat avg', value: formatDecimal(bat?.average) },
          { label: 'Strike rate', value: formatDecimal(bat?.strikeRate) },
          {
            label: 'High score',
            value:
              bat?.highestScore === undefined ? '–' : formatScore(bat.highestScore, bat.highestScoreNotOut),
          },
          {
            label: 'Wickets',
            value: formatInteger(bowl?.wickets),
            hint: bowl?.bestFigures && `Best ${bowl.bestFigures}`,
          },
          { label: 'Economy', value: formatDecimal(bowl?.economy) },
        ]}
      />
      <TabNav label="Player sections" tabs={TABS} />
      <StatsFilterBar state={filters} show={{ opponent: true, venue: true }} />
      <Outlet context={{ player: p, query: filters.query } satisfies PlayerContext} />
    </>
  );
}
