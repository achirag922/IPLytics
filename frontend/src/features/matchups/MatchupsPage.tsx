import { useMemo } from 'react';
import type { MatchupQuery } from '@/api/analyticsApi';
import { PlayerLink } from '@/components/cards/EntityLinks';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { FilterBar, PlayerPicker, SeasonRangeFilter } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { StatCardSkeleton } from '@/components/loading/Skeletons';
import { EmptyState } from '@/components/states/EmptyState';
import { QueryContent } from '@/components/states/QueryContent';
import { Badge } from '@/components/ui/badge';
import { useMatchupQuery } from '@/hooks/useAnalyticsQueries';
import { usePlayerQuery } from '@/hooks/usePlayerQueries';
import { useUrlState } from '@/hooks/useUrlState';
import { formatDecimal, formatInteger, formatPercent } from '@/lib/format';

function SelectedPlayer({ role, id }: { role: string; id: number | undefined }) {
  const player = usePlayerQuery(id);
  if (id === undefined) return null;
  return (
    <Badge variant="outline" className="py-1 text-sm">
      {role}: {player.data?.name ?? `#${id}`}
    </Badge>
  );
}

export function MatchupsPage() {
  const url = useUrlState();
  const batter = url.getNumber('batter');
  const bowler = url.getNumber('bowler');
  const seasonFrom = url.getNumber('seasonFrom');
  const seasonTo = url.getNumber('seasonTo');
  const query = useMemo<MatchupQuery | undefined>(
    () =>
      batter !== undefined && bowler !== undefined && batter !== bowler
        ? { batterId: batter, bowlerId: bowler, seasonFrom, seasonTo }
        : undefined,
    [batter, bowler, seasonFrom, seasonTo],
  );
  const matchup = useMatchupQuery(query);

  return (
    <>
      <PageHeader title="Matchups" description="Every ball a batter has faced from a bowler." />
      <FilterBar
        onReset={() =>
          url.update({ batter: undefined, bowler: undefined, seasonFrom: undefined, seasonTo: undefined })
        }
        resetDisabled={batter === undefined && bowler === undefined}
      >
        <PlayerPicker
          label="Batter"
          excludeIds={bowler ? [bowler] : []}
          onSelect={(p) => url.update({ batter: p.id })}
        />
        <PlayerPicker
          label="Bowler"
          excludeIds={batter ? [batter] : []}
          onSelect={(p) => url.update({ bowler: p.id })}
        />
        <SeasonRangeFilter
          from={seasonFrom}
          to={seasonTo}
          onChange={({ from, to }) => url.update({ seasonFrom: from, seasonTo: to })}
        />
      </FilterBar>
      <div className="mb-4 flex flex-wrap gap-2" aria-live="polite">
        <SelectedPlayer role="Batter" id={batter} />
        <SelectedPlayer role="Bowler" id={bowler} />
      </div>
      <QueryContent
        query={matchup}
        loading={<StatCardSkeleton count={8} />}
        idle={
          <EmptyState title="Choose a batter and a bowler" description="Search for both players above." />
        }
      >
        {(m) =>
          !m.occurred ? (
            <EmptyState
              title="No matchup found"
              description={`${m.batter?.name} has not faced ${m.bowler?.name} in the selected seasons.`}
            />
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                <PlayerLink player={m.batter} /> against <PlayerLink player={m.bowler} />,{' '}
                {formatInteger(m.innings)} innings
              </p>
              <StatGrid>
                <StatCard label="Balls" value={formatInteger(m.balls)} hint="Wides excluded" />
                <StatCard label="Runs" value={formatInteger(m.runs)} />
                <StatCard
                  label="Dismissals"
                  value={formatInteger(m.dismissals)}
                  hint={`Average ${formatDecimal(m.average)}`}
                />
                <StatCard label="Strike rate" value={formatDecimal(m.strikeRate)} />
                <StatCard label="Fours" value={formatInteger(m.fours)} />
                <StatCard label="Sixes" value={formatInteger(m.sixes)} />
                <StatCard
                  label="Dot %"
                  value={formatPercent(m.dotBallPercentage)}
                  hint={`${formatInteger(m.dotBalls)} dot balls`}
                />
                <StatCard label="Boundary %" value={formatPercent(m.boundaryPercentage)} />
              </StatGrid>
            </div>
          )
        }
      </QueryContent>
    </>
  );
}
