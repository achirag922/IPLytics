import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import type { HeadToHead } from '@/api/analyticsApi';
import type { Team, TeamStats } from '@/api/teamsApi';
import { VenueLink } from '@/components/cards/EntityLinks';
import { BarChart, ChartCard } from '@/components/charts';
import { ChartSkeleton, StatCardSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PhaseSection, TossSection } from '@/features/insights/InsightComponents';
import { usePhaseInsightsQuery, useTossInsightsQuery } from '@/hooks/useInsightQueries';
import { useTeamStatsQuery } from '@/hooks/useTeamQueries';
import { formatDecimal, formatInteger, formatPercent } from '@/lib/format';

interface HeadToHeadExtrasProps {
  data: HeadToHead;
  team1: Team;
  team2: Team;
  seasonFrom?: number;
  seasonTo?: number;
  venue?: number;
}

type VenueRow = NonNullable<HeadToHead['byVenue']>[number];

/** Venue split, toss and phases in the meetings, plus each franchise's overall record over the same seasons. */
export function HeadToHeadExtras({ data, team1, team2, seasonFrom, seasonTo, venue }: HeadToHeadExtrasProps) {
  const navigate = useNavigate();
  const meetings = useMemo(
    () => ({ team: team1.id, opponent: team2.id, seasonFrom, seasonTo, venue }),
    [team1.id, team2.id, seasonFrom, seasonTo, venue],
  );
  const toss = useTossInsightsQuery(meetings);
  const phases = usePhaseInsightsQuery(meetings);
  const overallQuery = useMemo(() => ({ seasonFrom, seasonTo }), [seasonFrom, seasonTo]);
  const stats1 = useTeamStatsQuery(team1.id, overallQuery);
  const stats2 = useTeamStatsQuery(team2.id, overallQuery);
  const venues = data.byVenue ?? [];
  const a = team1.code ?? 'Team 1';
  const b = team2.code ?? 'Team 2';

  return (
    <div className="space-y-6">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <Card>
          <CardHeader>
            <CardTitle>Meetings by venue</CardTitle>
          </CardHeader>
          <CardContent>
            <DataTable
              caption={`${a} v ${b} by venue`}
              exportFileName={`${a} v ${b} by venue`}
              columns={[
                { key: 'v', header: 'Venue', cell: (r: VenueRow) => <VenueLink venue={r.venue} /> },
                {
                  key: 'm',
                  header: 'M',
                  title: 'Matches',
                  align: 'right',
                  cell: (r) => formatInteger(r.matches),
                },
                { key: 'a', header: `${a} won`, align: 'right', cell: (r) => formatInteger(r.team1Wins) },
                { key: 'b', header: `${b} won`, align: 'right', cell: (r) => formatInteger(r.team2Wins) },
                {
                  key: 'nr',
                  header: 'NR',
                  title: 'No result',
                  align: 'right',
                  cell: (r) => formatInteger(r.noResults),
                },
              ]}
              rows={venues}
              rowKey={(r) => r.venue?.id ?? 0}
            />
          </CardContent>
        </Card>
        <ChartCard title="Wins by venue" description="Click a venue to open it">
          <BarChart
            horizontal
            height={Math.max(220, Math.min(venues.length, 10) * 30)}
            categories={venues.slice(0, 10).map((v) => v.venue?.name ?? '')}
            series={[
              { name: a, data: venues.slice(0, 10).map((v) => v.team1Wins), stack: 'w' },
              { name: b, data: venues.slice(0, 10).map((v) => v.team2Wins), stack: 'w' },
            ]}
            ariaLabel={`${a} and ${b} wins by venue`}
            onItemClick={(index) => {
              const id = venues[index]?.venue?.id;
              if (id !== undefined) navigate(`/venues/${id}`);
            }}
          />
        </ChartCard>
      </div>

      <section aria-labelledby="h2h-overall" className="space-y-3">
        <h2 id="h2h-overall" className="text-lg font-semibold">
          Overall records, same seasons
        </h2>
        {stats1.isPending || stats2.isPending ? (
          <TableSkeleton rows={6} columns={3} />
        ) : (
          <Card>
            <CardContent className="pt-4 sm:pt-5">
              <OverallTable a={a} b={b} s1={stats1.data} s2={stats2.data} />
            </CardContent>
          </Card>
        )}
      </section>

      <section aria-labelledby="h2h-phases" className="space-y-3">
        <h2 id="h2h-phases" className="text-lg font-semibold">
          Phases in their meetings
        </h2>
        <QueryContent
          query={phases}
          loading={<ChartSkeleton />}
          isEmpty={(d) => (d.batting ?? []).every((p) => p.legalBalls === 0)}
        >
          {(d) => (
            <PhaseSection
              data={d}
              subject={`${a} v ${b}`}
              battingLabel={`${a} batting`}
              bowlingLabel={`${b} batting`}
              plainLabels
            />
          )}
        </QueryContent>
      </section>

      <section aria-labelledby="h2h-toss" className="space-y-3">
        <h2 id="h2h-toss" className="text-lg font-semibold">
          Toss in their meetings
        </h2>
        <QueryContent
          query={toss}
          loading={<StatCardSkeleton />}
          isEmpty={(d) => (d.overall?.matches ?? 0) === 0}
        >
          {(d) => <TossSection data={d} subject={`${a} v ${b}`} />}
        </QueryContent>
      </section>
    </div>
  );
}

function OverallTable({ a, b, s1, s2 }: { a: string; b: string; s1?: TeamStats; s2?: TeamStats }) {
  const rows = [
    { label: 'Matches', a: formatInteger(s1?.overall?.played), b: formatInteger(s2?.overall?.played) },
    {
      label: 'Win %',
      a: formatPercent(s1?.overall?.winPercentage),
      b: formatPercent(s2?.overall?.winPercentage),
    },
    {
      label: 'Batting first win %',
      a: formatPercent(s1?.battingFirst?.winPercentage),
      b: formatPercent(s2?.battingFirst?.winPercentage),
    },
    {
      label: 'Chasing win %',
      a: formatPercent(s1?.chasing?.winPercentage),
      b: formatPercent(s2?.chasing?.winPercentage),
    },
    { label: 'Average score', a: formatDecimal(s1?.averageScore), b: formatDecimal(s2?.averageScore) },
    {
      label: 'Average conceded',
      a: formatDecimal(s1?.averageConceded),
      b: formatDecimal(s2?.averageConceded),
    },
    { label: 'Run rate', a: formatDecimal(s1?.runRate), b: formatDecimal(s2?.runRate) },
    { label: 'Highest score', a: formatInteger(s1?.highestScore), b: formatInteger(s2?.highestScore) },
  ];
  return (
    <>
      <CardDescription className="mb-2">Against every opponent, for the same season range</CardDescription>
      <DataTable
        caption={`${a} and ${b} overall records`}
        exportFileName={`${a} and ${b} overall`}
        columns={[
          { key: 'm', header: 'Measure', cell: (r: { label: string; a: string; b: string }) => r.label },
          { key: 'a', header: a, align: 'right', cell: (r) => r.a },
          { key: 'b', header: b, align: 'right', cell: (r) => r.b },
        ]}
        rows={rows}
        rowKey={(r) => r.label}
      />
    </>
  );
}
