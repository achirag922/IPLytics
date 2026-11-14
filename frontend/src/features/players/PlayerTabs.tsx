import type { BattingSummary, BowlingSummary } from '@/api/playersApi';
import { TeamLink, VenueLink } from '@/components/cards/EntityLinks';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { BarChart, ChartCard, LineChart } from '@/components/charts';
import { ChartSkeleton, StatCardSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { EmptyState } from '@/components/states/EmptyState';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  usePlayerBattingQuery,
  usePlayerBowlingQuery,
  usePlayerPhasesQuery,
  usePlayerVenuesQuery,
  usePlayerVsTeamsQuery,
} from '@/hooks/usePlayerQueries';
import { formatDecimal, formatInteger, formatPercent, formatScore } from '@/lib/format';
import { usePlayerContext } from '@/features/detailContext';

const NO_STATS = 'No statistics available for the selected filters';
const PHASE_LABEL: Record<string, string> = {
  powerplay: 'Powerplay (1–6)',
  middle: 'Middle (7–15)',
  death: 'Death (16–20)',
};

function BattingCards({ s }: { s: BattingSummary }) {
  return (
    <StatGrid>
      <StatCard
        label="Runs"
        value={formatInteger(s.runs)}
        hint={`${formatInteger(s.innings)} innings, ${formatInteger(s.notOuts)} not out`}
      />
      <StatCard label="Average" value={formatDecimal(s.average)} />
      <StatCard
        label="Strike rate"
        value={formatDecimal(s.strikeRate)}
        hint={`${formatInteger(s.balls)} balls`}
      />
      <StatCard
        label="Highest score"
        value={s.highestScore === undefined ? '–' : formatScore(s.highestScore, s.highestScoreNotOut)}
        hint={`${s.hundreds} hundreds, ${s.fifties} fifties`}
      />
      <StatCard
        label="Fours / sixes"
        value={`${formatInteger(s.fours)} / ${formatInteger(s.sixes)}`}
        hint={`Boundary ${formatPercent(s.boundaryPercentage)}`}
      />
      <StatCard
        label="Dot balls"
        value={formatPercent(s.dotBallPercentage)}
        hint={`${formatInteger(s.dotBalls)} dots`}
      />
      <StatCard label="Ducks" value={formatInteger(s.ducks)} />
      <StatCard label="Balls per boundary" value={formatDecimal(s.ballsPerBoundary)} />
    </StatGrid>
  );
}

function BowlingCards({ s }: { s: BowlingSummary }) {
  return (
    <StatGrid>
      <StatCard
        label="Wickets"
        value={formatInteger(s.wickets)}
        hint={`${formatInteger(s.innings)} innings`}
      />
      <StatCard label="Economy" value={formatDecimal(s.economy)} hint={`${s.overs ?? '–'} overs`} />
      <StatCard label="Average" value={formatDecimal(s.average)} />
      <StatCard label="Strike rate" value={formatDecimal(s.strikeRate)} hint="Balls per wicket" />
      <StatCard
        label="Best figures"
        value={s.bestFigures ?? '–'}
        hint={`${s.fourWicketHauls} four-fors, ${s.fiveWicketHauls} five-fors`}
      />
      <StatCard
        label="Dot balls"
        value={formatPercent(s.dotBallPercentage)}
        hint={`${formatInteger(s.dotBalls)} dots`}
      />
      <StatCard label="Runs conceded" value={formatInteger(s.runsConceded)} />
      <StatCard label="Wides / no-balls" value={`${formatInteger(s.wides)} / ${formatInteger(s.noBalls)}`} />
    </StatGrid>
  );
}

export function PlayerOverviewTab() {
  const { player, query } = usePlayerContext();
  const batting = usePlayerBattingQuery(player.id, query);
  const bowling = usePlayerBowlingQuery(player.id, query);
  return (
    <div className="space-y-6">
      <section aria-labelledby="ov-bat" className="space-y-3">
        <h2 id="ov-bat" className="text-lg font-semibold">
          Batting
        </h2>
        <QueryContent
          query={batting}
          loading={<StatCardSkeleton count={8} />}
          isEmpty={(d) => (d.summary?.innings ?? 0) === 0}
          emptyTitle="Did not bat in the selected matches"
        >
          {(d) => <BattingCards s={d.summary as BattingSummary} />}
        </QueryContent>
      </section>
      <section aria-labelledby="ov-bowl" className="space-y-3">
        <h2 id="ov-bowl" className="text-lg font-semibold">
          Bowling
        </h2>
        <QueryContent
          query={bowling}
          loading={<StatCardSkeleton count={8} />}
          isEmpty={(d) => (d.summary?.innings ?? 0) === 0}
          emptyTitle="Did not bowl in the selected matches"
        >
          {(d) => <BowlingCards s={d.summary as BowlingSummary} />}
        </QueryContent>
      </section>
      <Card>
        <CardHeader>
          <CardTitle>Franchises</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            caption={`Franchises ${player.name} played for`}
            columns={[
              { key: 'team', header: 'Team', cell: (t) => <TeamLink team={t.team} /> },
              {
                key: 'span',
                header: 'Seasons',
                cell: (t) =>
                  t.firstSeason === t.lastSeason ? t.firstSeason : `${t.firstSeason}–${t.lastSeason}`,
              },
              { key: 'm', header: 'Matches', align: 'right', cell: (t) => formatInteger(t.matches) },
            ]}
            rows={player.teams ?? []}
            rowKey={(t) => `${t.team?.id}-${t.firstSeason}`}
          />
        </CardContent>
      </Card>
    </div>
  );
}

export function PlayerBattingTab() {
  const { player, query } = usePlayerContext();
  const batting = usePlayerBattingQuery(player.id, query);
  return (
    <QueryContent
      query={batting}
      loading={<StatCardSkeleton count={8} />}
      isEmpty={(d) => (d.summary?.innings ?? 0) === 0}
      emptyTitle={NO_STATS}
    >
      {(d) => {
        const seasons = d.bySeason ?? [];
        const years = seasons.map((s) => s.season);
        return (
          <div className="space-y-4">
            <BattingCards s={d.summary as BattingSummary} />
            <div className="grid gap-4 lg:grid-cols-2">
              <ChartCard title="Runs by season">
                <BarChart
                  height={260}
                  categories={years}
                  series={[{ name: 'Runs', data: seasons.map((s) => s.stats?.runs) }]}
                  ariaLabel={`${player.name} runs per season`}
                />
              </ChartCard>
              <ChartCard title="Strike rate by season">
                <LineChart
                  height={260}
                  categories={years}
                  series={[{ name: 'Strike rate', data: seasons.map((s) => s.stats?.strikeRate) }]}
                  ariaLabel={`${player.name} strike rate per season`}
                />
              </ChartCard>
              <ChartCard title="Average by season" description="No average in a season without a dismissal">
                <LineChart
                  height={260}
                  categories={years}
                  series={[{ name: 'Average', data: seasons.map((s) => s.stats?.average) }]}
                  ariaLabel={`${player.name} batting average per season`}
                />
              </ChartCard>
              <ChartCard title="Boundary distribution" description="Fours and sixes hit each season">
                <BarChart
                  height={260}
                  categories={years}
                  series={[
                    { name: 'Fours', data: seasons.map((s) => s.stats?.fours), stack: 'b' },
                    { name: 'Sixes', data: seasons.map((s) => s.stats?.sixes), stack: 'b' },
                  ]}
                  ariaLabel={`${player.name} fours and sixes per season`}
                />
              </ChartCard>
            </div>
            <Card>
              <CardHeader>
                <CardTitle>Season by season</CardTitle>
              </CardHeader>
              <CardContent>
                <DataTable
                  caption={`${player.name} batting by season`}
                  columns={[
                    { key: 's', header: 'Season', cell: (r) => r.season },
                    {
                      key: 'm',
                      header: 'M',
                      title: 'Matches',
                      align: 'right',
                      cell: (r) => formatInteger(r.stats?.matches),
                    },
                    {
                      key: 'i',
                      header: 'Inns',
                      title: 'Innings',
                      align: 'right',
                      cell: (r) => formatInteger(r.stats?.innings),
                    },
                    { key: 'r', header: 'Runs', align: 'right', cell: (r) => formatInteger(r.stats?.runs) },
                    { key: 'b', header: 'Balls', align: 'right', cell: (r) => formatInteger(r.stats?.balls) },
                    {
                      key: 'a',
                      header: 'Avg',
                      title: 'Average',
                      align: 'right',
                      cell: (r) => formatDecimal(r.stats?.average),
                    },
                    {
                      key: 'sr',
                      header: 'SR',
                      title: 'Strike rate',
                      align: 'right',
                      cell: (r) => formatDecimal(r.stats?.strikeRate),
                    },
                    {
                      key: 'hs',
                      header: 'HS',
                      title: 'Highest score',
                      align: 'right',
                      cell: (r) =>
                        r.stats?.highestScore === undefined
                          ? '–'
                          : formatScore(r.stats.highestScore, r.stats.highestScoreNotOut),
                    },
                    { key: '4', header: '4s', align: 'right', cell: (r) => formatInteger(r.stats?.fours) },
                    { key: '6', header: '6s', align: 'right', cell: (r) => formatInteger(r.stats?.sixes) },
                    {
                      key: '50',
                      header: '50s',
                      align: 'right',
                      cell: (r) => formatInteger(r.stats?.fifties),
                    },
                    {
                      key: '100',
                      header: '100s',
                      align: 'right',
                      cell: (r) => formatInteger(r.stats?.hundreds),
                    },
                  ]}
                  rows={[...seasons].reverse()}
                  rowKey={(r) => r.season}
                />
              </CardContent>
            </Card>
          </div>
        );
      }}
    </QueryContent>
  );
}

export function PlayerBowlingTab() {
  const { player, query } = usePlayerContext();
  const bowling = usePlayerBowlingQuery(player.id, query);
  return (
    <QueryContent
      query={bowling}
      loading={<StatCardSkeleton count={8} />}
      isEmpty={(d) => (d.summary?.innings ?? 0) === 0}
      emptyTitle={NO_STATS}
    >
      {(d) => {
        const seasons = d.bySeason ?? [];
        const years = seasons.map((s) => s.season);
        return (
          <div className="space-y-4">
            <BowlingCards s={d.summary as BowlingSummary} />
            <div className="grid gap-4 lg:grid-cols-3">
              <ChartCard title="Wickets by season">
                <BarChart
                  height={240}
                  categories={years}
                  series={[{ name: 'Wickets', data: seasons.map((s) => s.stats?.wickets), color: '#059669' }]}
                  ariaLabel={`${player.name} wickets per season`}
                />
              </ChartCard>
              <ChartCard title="Economy by season">
                <LineChart
                  height={240}
                  categories={years}
                  series={[{ name: 'Economy', data: seasons.map((s) => s.stats?.economy) }]}
                  ariaLabel={`${player.name} economy per season`}
                />
              </ChartCard>
              <ChartCard title="Average by season">
                <LineChart
                  height={240}
                  categories={years}
                  series={[{ name: 'Average', data: seasons.map((s) => s.stats?.average) }]}
                  ariaLabel={`${player.name} bowling average per season`}
                />
              </ChartCard>
            </div>
            <Card>
              <CardHeader>
                <CardTitle>Season by season</CardTitle>
              </CardHeader>
              <CardContent>
                <DataTable
                  caption={`${player.name} bowling by season`}
                  columns={[
                    { key: 's', header: 'Season', cell: (r) => r.season },
                    {
                      key: 'i',
                      header: 'Inns',
                      title: 'Innings',
                      align: 'right',
                      cell: (r) => formatInteger(r.stats?.innings),
                    },
                    { key: 'o', header: 'Overs', align: 'right', cell: (r) => r.stats?.overs ?? '–' },
                    {
                      key: 'r',
                      header: 'Runs',
                      align: 'right',
                      cell: (r) => formatInteger(r.stats?.runsConceded),
                    },
                    {
                      key: 'w',
                      header: 'Wkts',
                      title: 'Wickets',
                      align: 'right',
                      cell: (r) => formatInteger(r.stats?.wickets),
                    },
                    {
                      key: 'e',
                      header: 'Econ',
                      title: 'Economy',
                      align: 'right',
                      cell: (r) => formatDecimal(r.stats?.economy),
                    },
                    {
                      key: 'a',
                      header: 'Avg',
                      title: 'Average',
                      align: 'right',
                      cell: (r) => formatDecimal(r.stats?.average),
                    },
                    {
                      key: 'sr',
                      header: 'SR',
                      title: 'Strike rate',
                      align: 'right',
                      cell: (r) => formatDecimal(r.stats?.strikeRate),
                    },
                    { key: 'bf', header: 'Best', align: 'right', cell: (r) => r.stats?.bestFigures ?? '–' },
                    {
                      key: 'd',
                      header: 'Dot %',
                      align: 'right',
                      cell: (r) => formatPercent(r.stats?.dotBallPercentage),
                    },
                  ]}
                  rows={[...seasons].reverse()}
                  rowKey={(r) => r.season}
                />
              </CardContent>
            </Card>
          </div>
        );
      }}
    </QueryContent>
  );
}

export function PlayerPhasesTab() {
  const { player, query } = usePlayerContext();
  const phases = usePlayerPhasesQuery(player.id, query);
  return (
    <QueryContent query={phases} loading={<ChartSkeleton />}>
      {(d) => {
        const batting = (d.batting ?? []).filter((p) => p.balls > 0);
        const bowling = (d.bowling ?? []).filter((p) => p.legalBalls > 0);
        if (batting.length === 0 && bowling.length === 0) return <EmptyState title={NO_STATS} />;
        return (
          <div className="space-y-4">
            {batting.length > 0 && (
              <div className="grid gap-4 xl:grid-cols-2">
                <Card>
                  <CardHeader>
                    <CardTitle>Batting by phase</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <DataTable
                      caption={`${player.name} batting by phase of the innings`}
                      columns={[
                        { key: 'p', header: 'Phase', cell: (r) => PHASE_LABEL[r.phase ?? ''] ?? r.phase },
                        { key: 'r', header: 'Runs', align: 'right', cell: (r) => formatInteger(r.runs) },
                        { key: 'b', header: 'Balls', align: 'right', cell: (r) => formatInteger(r.balls) },
                        {
                          key: 'sr',
                          header: 'SR',
                          title: 'Strike rate',
                          align: 'right',
                          cell: (r) => formatDecimal(r.strikeRate),
                        },
                        { key: '4', header: '4s', align: 'right', cell: (r) => formatInteger(r.fours) },
                        { key: '6', header: '6s', align: 'right', cell: (r) => formatInteger(r.sixes) },
                        {
                          key: 'bp',
                          header: 'Boundary %',
                          align: 'right',
                          cell: (r) => formatPercent(r.boundaryPercentage),
                        },
                        {
                          key: 'sh',
                          header: 'Run share',
                          align: 'right',
                          cell: (r) => formatPercent(r.runShare),
                        },
                      ]}
                      rows={batting}
                      rowKey={(r) => r.phase ?? ''}
                    />
                  </CardContent>
                </Card>
                <ChartCard title="Strike rate and boundary % by phase">
                  <BarChart
                    height={260}
                    categories={batting.map((p) => PHASE_LABEL[p.phase ?? ''] ?? p.phase ?? '')}
                    series={[
                      { name: 'Strike rate', data: batting.map((p) => p.strikeRate) },
                      { name: 'Boundary %', data: batting.map((p) => p.boundaryPercentage) },
                    ]}
                    ariaLabel={`${player.name} strike rate and boundary percentage by phase`}
                  />
                </ChartCard>
              </div>
            )}
            {bowling.length > 0 && (
              <div className="grid gap-4 xl:grid-cols-2">
                <Card>
                  <CardHeader>
                    <CardTitle>Bowling by phase</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <DataTable
                      caption={`${player.name} bowling by phase of the innings`}
                      columns={[
                        { key: 'p', header: 'Phase', cell: (r) => PHASE_LABEL[r.phase ?? ''] ?? r.phase },
                        { key: 'o', header: 'Overs', align: 'right', cell: (r) => r.overs ?? '–' },
                        {
                          key: 'r',
                          header: 'Runs',
                          align: 'right',
                          cell: (r) => formatInteger(r.runsConceded),
                        },
                        {
                          key: 'w',
                          header: 'Wkts',
                          title: 'Wickets',
                          align: 'right',
                          cell: (r) => formatInteger(r.wickets),
                        },
                        {
                          key: 'e',
                          header: 'Econ',
                          title: 'Economy',
                          align: 'right',
                          cell: (r) => formatDecimal(r.economy),
                        },
                      ]}
                      rows={bowling}
                      rowKey={(r) => r.phase ?? ''}
                    />
                  </CardContent>
                </Card>
                <ChartCard title="Economy by phase">
                  <BarChart
                    height={260}
                    categories={bowling.map((p) => PHASE_LABEL[p.phase ?? ''] ?? p.phase ?? '')}
                    series={[{ name: 'Economy', data: bowling.map((p) => p.economy), color: '#059669' }]}
                    ariaLabel={`${player.name} economy by phase`}
                  />
                </ChartCard>
              </div>
            )}
          </div>
        );
      }}
    </QueryContent>
  );
}

interface SplitRow {
  key: number;
  label: React.ReactNode;
  matches: number;
  batting?: BattingSummary;
  bowling?: BowlingSummary;
}

const splitColumns = (labelHeader: string): Column<SplitRow>[] => [
  { key: 'label', header: labelHeader, cell: (r) => r.label },
  { key: 'm', header: 'M', title: 'Matches', align: 'right', cell: (r) => formatInteger(r.matches) },
  { key: 'r', header: 'Runs', align: 'right', cell: (r) => formatInteger(r.batting?.runs) },
  {
    key: 'a',
    header: 'Avg',
    title: 'Batting average',
    align: 'right',
    cell: (r) => formatDecimal(r.batting?.average),
  },
  {
    key: 'sr',
    header: 'SR',
    title: 'Strike rate',
    align: 'right',
    cell: (r) => formatDecimal(r.batting?.strikeRate),
  },
  {
    key: 'hs',
    header: 'HS',
    title: 'Highest score',
    align: 'right',
    cell: (r) =>
      r.batting?.highestScore === undefined
        ? '–'
        : formatScore(r.batting.highestScore, r.batting.highestScoreNotOut),
  },
  {
    key: 'w',
    header: 'Wkts',
    title: 'Wickets',
    align: 'right',
    cell: (r) => formatInteger(r.bowling?.wickets),
  },
  {
    key: 'e',
    header: 'Econ',
    title: 'Economy',
    align: 'right',
    cell: (r) => formatDecimal(r.bowling?.economy),
  },
  { key: 'bf', header: 'Best', align: 'right', cell: (r) => r.bowling?.bestFigures ?? '–' },
];

export function PlayerVsTeamsTab() {
  const { player, query } = usePlayerContext();
  const vsTeams = usePlayerVsTeamsQuery(player.id, query);
  return (
    <Card>
      <CardContent className="pt-4 sm:pt-5">
        <QueryContent
          query={vsTeams}
          loading={<TableSkeleton columns={9} />}
          isEmpty={(d) => (d.opponents ?? []).length === 0}
          emptyTitle={NO_STATS}
        >
          {(d) => (
            <DataTable
              caption={`${player.name} against each opponent`}
              columns={splitColumns('Opponent')}
              rows={(d.opponents ?? []).map((o) => ({
                key: o.opponent?.id ?? 0,
                label: <TeamLink team={o.opponent} />,
                matches: o.matches,
                batting: o.batting,
                bowling: o.bowling,
              }))}
              rowKey={(r) => r.key}
            />
          )}
        </QueryContent>
      </CardContent>
    </Card>
  );
}

export function PlayerVenuesTab() {
  const { player, query } = usePlayerContext();
  const venues = usePlayerVenuesQuery(player.id, query);
  return (
    <Card>
      <CardContent className="pt-4 sm:pt-5">
        <QueryContent
          query={venues}
          loading={<TableSkeleton columns={9} />}
          isEmpty={(d) => (d.venues ?? []).length === 0}
          emptyTitle={NO_STATS}
        >
          {(d) => (
            <DataTable
              caption={`${player.name} at each venue`}
              columns={splitColumns('Venue')}
              rows={(d.venues ?? []).map((v) => ({
                key: v.venue?.id ?? 0,
                label: <VenueLink venue={v.venue} />,
                matches: v.matches,
                batting: v.batting,
                bowling: v.bowling,
              }))}
              rowKey={(r) => r.key}
            />
          )}
        </QueryContent>
      </CardContent>
    </Card>
  );
}
