import { useMemo } from 'react';
import type { HeadToHeadQuery } from '@/api/analyticsApi';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { TeamLink } from '@/components/cards/EntityLinks';
import { BarChart, ChartCard } from '@/components/charts';
import { FilterBar, SeasonRangeFilter, TeamFilter, VenueFilter } from '@/components/filters';
import { PageHeader } from '@/components/layout/PageHeader';
import { StatCardSkeleton } from '@/components/loading/Skeletons';
import { EmptyState } from '@/components/states/EmptyState';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useHeadToHeadQuery } from '@/hooks/useAnalyticsQueries';
import { useTeamLookup } from '@/hooks/useTeamQueries';
import { useUrlState } from '@/hooks/useUrlState';
import { formatDecimal, formatInteger } from '@/lib/format';
import { matchColumns } from '@/features/matches/matchColumns';

export function HeadToHeadPage() {
  const url = useUrlState();
  const lookup = useTeamLookup();
  const team1Code = url.get('team1')?.toUpperCase();
  const team2Code = url.get('team2')?.toUpperCase();
  const seasonFrom = url.getNumber('seasonFrom');
  const seasonTo = url.getNumber('seasonTo');
  const venue = url.getNumber('venue');
  const team1 = lookup.byCode(team1Code);
  const team2 = lookup.byCode(team2Code);

  const query = useMemo<HeadToHeadQuery | undefined>(
    () =>
      team1 && team2 && team1.id !== team2.id
        ? { team1: team1.id, team2: team2.id, seasonFrom, seasonTo, venue }
        : undefined,
    [team1, team2, seasonFrom, seasonTo, venue],
  );
  const h2h = useHeadToHeadQuery(query);

  return (
    <>
      <PageHeader title="Head to Head" description="How two franchises have fared against each other." />
      <FilterBar
        onReset={() =>
          url.update({
            team1: undefined,
            team2: undefined,
            seasonFrom: undefined,
            seasonTo: undefined,
            venue: undefined,
          })
        }
        resetDisabled={!team1Code && !team2Code && !seasonFrom && !seasonTo && !venue}
      >
        <TeamFilter
          label="Team 1"
          allLabel="Choose a team"
          value={team1Code}
          exclude={team2Code ? [team2Code] : []}
          onChange={(team1) => url.update({ team1 })}
        />
        <TeamFilter
          label="Team 2"
          allLabel="Choose a team"
          value={team2Code}
          exclude={team1Code ? [team1Code] : []}
          onChange={(team2) => url.update({ team2 })}
        />
        <SeasonRangeFilter
          from={seasonFrom}
          to={seasonTo}
          onChange={({ from, to }) => url.update({ seasonFrom: from, seasonTo: to })}
        />
        <VenueFilter value={venue} onChange={(v) => url.update({ venue: v })} />
      </FilterBar>

      <QueryContent
        query={h2h}
        loading={<StatCardSkeleton count={4} />}
        idle={
          <EmptyState
            title="Choose two teams"
            description="Pick two different franchises to compare their record against each other."
          />
        }
        isEmpty={(d) => d.matches === 0}
        emptyTitle="These teams have not met"
        emptyDescription="No matches between them for the selected seasons and venue."
      >
        {(d) => {
          const a = d.team1;
          const b = d.team2;
          const nameA = a?.team?.code ?? 'Team 1';
          const nameB = b?.team?.code ?? 'Team 2';
          return (
            <div className="space-y-4">
              <StatGrid className="lg:grid-cols-5">
                <StatCard label="Matches" value={formatInteger(d.matches)} />
                <StatCard
                  label={`${nameA} wins`}
                  value={formatInteger(a?.wins)}
                  hint={<TeamLink team={a?.team} />}
                />
                <StatCard
                  label={`${nameB} wins`}
                  value={formatInteger(b?.wins)}
                  hint={<TeamLink team={b?.team} />}
                />
                <StatCard label="Ties" value={formatInteger(d.ties)} hint="Decided by super over" />
                <StatCard label="No results" value={formatInteger(d.noResults)} />
              </StatGrid>
              <div className="grid gap-4 lg:grid-cols-2">
                <ChartCard title="Results compared" description="Wins by batting first and chasing">
                  <BarChart
                    height={280}
                    categories={['Total wins', 'Batting first', 'Chasing']}
                    series={[
                      { name: nameA, data: [a?.wins, a?.winsBattingFirst, a?.winsChasing] },
                      { name: nameB, data: [b?.wins, b?.winsBattingFirst, b?.winsChasing] },
                    ]}
                    ariaLabel={`${nameA} and ${nameB} wins, wins batting first and wins chasing`}
                  />
                </ChartCard>
                <Card>
                  <CardHeader>
                    <CardTitle>Side by side</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <DataTable
                      caption={`${nameA} and ${nameB} compared`}
                      columns={[
                        {
                          key: 'm',
                          header: 'Measure',
                          cell: (r: { label: string; a: string; b: string }) => r.label,
                        },
                        { key: 'a', header: nameA, align: 'right', cell: (r) => r.a },
                        { key: 'b', header: nameB, align: 'right', cell: (r) => r.b },
                      ]}
                      rows={[
                        { label: 'Wins', a: formatInteger(a?.wins), b: formatInteger(b?.wins) },
                        {
                          label: 'Wins batting first',
                          a: formatInteger(a?.winsBattingFirst),
                          b: formatInteger(b?.winsBattingFirst),
                        },
                        {
                          label: 'Wins chasing',
                          a: formatInteger(a?.winsChasing),
                          b: formatInteger(b?.winsChasing),
                        },
                        {
                          label: 'Average score',
                          a: formatDecimal(a?.averageScore),
                          b: formatDecimal(b?.averageScore),
                        },
                      ]}
                      rowKey={(r) => r.label}
                    />
                  </CardContent>
                </Card>
              </div>
              {(d.bySeason ?? []).length > 1 && (
                <ChartCard title="Wins by season">
                  <BarChart
                    height={260}
                    categories={(d.bySeason ?? []).map((s) => s.season)}
                    series={[
                      { name: nameA, data: (d.bySeason ?? []).map((s) => s.team1Wins), stack: 'w' },
                      { name: nameB, data: (d.bySeason ?? []).map((s) => s.team2Wins), stack: 'w' },
                    ]}
                    ariaLabel={`${nameA} and ${nameB} wins in each season`}
                  />
                </ChartCard>
              )}
              <Card>
                <CardHeader>
                  <CardTitle>Recent matches</CardTitle>
                </CardHeader>
                <CardContent>
                  <DataTable
                    caption="Latest meetings"
                    columns={matchColumns}
                    rows={d.recentMatches ?? []}
                    rowKey={(m) => m.id}
                    rowHref={(m) => `/matches/${m.id}`}
                  />
                </CardContent>
              </Card>
            </div>
          );
        }}
      </QueryContent>
    </>
  );
}
