import { Outlet } from 'react-router';
import type { TeamContext } from '@/features/detailContext';
import { ApiError } from '@/api/client';
import { Badge } from '@/components/ui/badge';
import { StatsFilterBar } from '@/components/filters';
import { EntityHero } from '@/components/layout/EntityHero';
import { TeamLogo } from '@/components/media/TeamLogo';
import { teamBranding } from '@/config/media/teamBranding';
import { useSeasonsQuery } from '@/hooks/useSeasonQueries';
import { formatInteger, formatPercent } from '@/lib/format';
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
  { label: 'Phases', to: 'phases' },
  { label: 'Toss & form', to: 'toss' },
];

export function TeamDetailPage() {
  const teamId = useNumericParam('teamId');
  const team = useTeamQuery(teamId);
  const filters = useStatsFilters();
  const seasons = useSeasonsQuery();

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
  // Seasons whose Final this franchise won (the champion comes from the seasons API).
  const titles = (seasons.data ?? []).filter((s) => s.champion?.id === t.id).map((s) => s.year);
  return (
    <>
      <EntityHero
        breadcrumbs={[{ label: 'Teams', to: '/teams' }, { label: t.name ?? '' }]}
        accent={teamBranding(t.code, t.name)}
        media={<TeamLogo team={t} size="xl" label={`${t.name} crest`} />}
        eyebrow={<>Franchise{t.homeCity && <> · {t.homeCity}</>}</>}
        title={t.name}
        subtitle={
          <>
            {t.firstSeason}–{t.active ? 'present' : t.lastSeason}
            {formerNames.length > 0 && <> · Also played as {formerNames.map((n) => n.name).join(', ')}</>}
          </>
        }
        badges={
          <>
            <Badge variant="outline">{t.code}</Badge>
            {!t.active && <Badge variant="destructive">Defunct</Badge>}
          </>
        }
        stats={[
          { label: 'Matches', value: formatInteger(t.record?.played) },
          { label: 'Won', value: formatInteger(t.record?.won) },
          { label: 'Lost', value: formatInteger(t.record?.lost) },
          { label: 'Win %', value: formatPercent(t.record?.winPercentage) },
          {
            label: 'Titles',
            value: formatInteger(titles.length),
            hint: titles.length > 0 ? titles.join(', ') : undefined,
          },
          ...(t.latestSeason
            ? [
                {
                  label: `Season ${t.latestSeason.season}`,
                  value: `${t.latestSeason.record?.won ?? 0}–${t.latestSeason.record?.lost ?? 0}`,
                  hint: 'Won–lost',
                },
              ]
            : []),
        ]}
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
