import { useNavigate } from 'react-router';
import type { PlayerConsistency } from '@/api/playersApi';
import { TeamLink } from '@/components/cards/EntityLinks';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { BarChart, ChartCard } from '@/components/charts';
import { StatCardSkeleton } from '@/components/loading/Skeletons';
import { EmptyState } from '@/components/states/EmptyState';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { usePlayerContext } from '@/features/detailContext';
import { usePlayerConsistencyQuery } from '@/hooks/useInsightQueries';
import { formatDate, formatDecimal, formatInteger, formatPercent, formatScore } from '@/lib/format';

type Batting = NonNullable<PlayerConsistency['batting']>;
type Bowling = NonNullable<PlayerConsistency['bowling']>;

const formLabel = (date: string | undefined, opponent: string | undefined) =>
  `${formatDate(date)} v ${opponent ?? '?'}`;

function BattingConsistency({ b, name }: { b: Batting; name: string }) {
  const navigate = useNavigate();
  const form = [...(b.recentInnings ?? [])].reverse(); // oldest first, left to right
  return (
    <section aria-labelledby="cons-bat" className="space-y-4">
      <h2 id="cons-bat" className="text-lg font-semibold">
        Batting consistency
      </h2>
      <StatGrid>
        <StatCard
          label="Median score"
          value={formatDecimal(b.medianScore)}
          hint={`Average ${formatDecimal(b.average)}`}
        />
        <StatCard
          label="Spread"
          value={formatDecimal(b.standardDeviation)}
          hint="Standard deviation of scores"
        />
        <StatCard
          label="Variation"
          value={formatPercent(b.coefficientOfVariation)}
          hint="Lower is steadier"
        />
        <StatCard
          label="30+ innings"
          value={formatPercent(b.thirtyPlusPercentage)}
          hint={`50+: ${formatPercent(b.fiftyPlusPercentage)}`}
        />
        <StatCard
          label="Under 10"
          value={formatPercent(b.underTenPercentage)}
          hint={`${formatInteger(b.innings)} innings`}
        />
      </StatGrid>
      <div className="grid gap-4 xl:grid-cols-2">
        <ChartCard title="Score distribution" description="Innings by score band">
          <BarChart
            height={240}
            categories={(b.scoreBands ?? []).map((s) => s.label ?? '')}
            series={[{ name: 'Innings', data: (b.scoreBands ?? []).map((s) => s.innings) }]}
            ariaLabel={`${name} innings by score band`}
          />
        </ChartCard>
        <ChartCard
          title="Recent form"
          description="Last innings, oldest to newest; click a bar to open the match"
        >
          <BarChart
            height={240}
            categories={form.map((i) => formLabel(i.date, i.opponent?.code))}
            series={[{ name: 'Runs', data: form.map((i) => i.runs) }]}
            ariaLabel={`${name} runs in recent innings`}
            onItemClick={(index) => {
              const innings = form[index];
              if (innings) navigate(`/matches/${innings.matchId}`);
            }}
          />
        </ChartCard>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Recent innings</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            caption={`${name} recent batting innings`}
            exportFileName={`${name} recent batting`}
            columns={[
              { key: 'd', header: 'Date', cell: (i) => formatDate(i.date) },
              { key: 'o', header: 'Opponent', cell: (i) => <TeamLink team={i.opponent} short /> },
              { key: 'r', header: 'Runs', align: 'right', cell: (i) => formatScore(i.runs, i.notOut) },
              { key: 'b', header: 'Balls', align: 'right', cell: (i) => i.balls },
              {
                key: 'sr',
                header: 'SR',
                title: 'Strike rate',
                align: 'right',
                cell: (i) => formatDecimal(i.strikeRate),
              },
            ]}
            rows={b.recentInnings ?? []}
            rowKey={(i) => `${i.matchId}-${i.runs}-${i.balls}`}
            rowHref={(i) => `/matches/${i.matchId}`}
          />
        </CardContent>
      </Card>
    </section>
  );
}

function BowlingConsistency({ b, name }: { b: Bowling; name: string }) {
  const navigate = useNavigate();
  const form = [...(b.recentInnings ?? [])].reverse();
  return (
    <section aria-labelledby="cons-bowl" className="space-y-4">
      <h2 id="cons-bowl" className="text-lg font-semibold">
        Bowling consistency
      </h2>
      <StatGrid>
        <StatCard
          label="Wicket-taking innings"
          value={formatPercent(b.wicketInningsPercentage)}
          hint={`${formatInteger(b.innings)} innings`}
        />
        <StatCard
          label="2+ wickets"
          value={formatPercent(b.multiWicketInningsPercentage)}
          hint={`Median ${formatDecimal(b.medianWickets)} wickets`}
        />
        <StatCard
          label="Economy spread"
          value={formatDecimal(b.economyStandardDeviation)}
          hint={`Variation ${formatPercent(b.economyCoefficientOfVariation)}`}
        />
        <StatCard
          label="Economical innings"
          value={formatPercent(b.economicalInningsPercentage)}
          hint="Under 8 an over"
        />
      </StatGrid>
      <ChartCard
        title="Recent form"
        description="Wickets in the last innings, oldest to newest; click to open the match"
      >
        <BarChart
          height={240}
          categories={form.map((i) => formLabel(i.date, i.opponent?.code))}
          series={[
            { name: 'Wickets', data: form.map((i) => i.wickets), color: '#059669' },
            { name: 'Economy', data: form.map((i) => i.economy) },
          ]}
          ariaLabel={`${name} wickets and economy in recent innings`}
          onItemClick={(index) => {
            const innings = form[index];
            if (innings) navigate(`/matches/${innings.matchId}`);
          }}
        />
      </ChartCard>
    </section>
  );
}

export function PlayerConsistencyTab() {
  const { player, query } = usePlayerContext();
  const consistency = usePlayerConsistencyQuery(player.id, query);
  return (
    <QueryContent query={consistency} loading={<StatCardSkeleton count={8} />}>
      {(d) => {
        const batted = (d.batting?.innings ?? 0) > 0;
        const bowled = (d.bowling?.innings ?? 0) > 0;
        if (!batted && !bowled)
          return <EmptyState title="No statistics available for the selected filters" />;
        return (
          <div className="space-y-8">
            {batted && d.batting && <BattingConsistency b={d.batting} name={player.name ?? ''} />}
            {bowled && d.bowling && <BowlingConsistency b={d.bowling} name={player.name ?? ''} />}
          </div>
        );
      }}
    </QueryContent>
  );
}
