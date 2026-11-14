import type { InningsScorecard } from '@/api/matchesApi';
import { PlayerLink } from '@/components/cards/EntityLinks';
import { ChartCard, Heatmap, ManhattanChart, WormChart, type InningsSeries } from '@/components/charts';
import { ChartSkeleton, TableSkeleton } from '@/components/loading/Skeletons';
import { QueryContent } from '@/components/states/QueryContent';
import { DataTable } from '@/components/tables/DataTable';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useMatchProgressionQuery, useScorecardQuery } from '@/hooks/useMatchQueries';
import { formatDecimal, formatInteger } from '@/lib/format';
import { useMatchContext } from '@/features/detailContext';

function InningsCard({ innings }: { innings: InningsScorecard }) {
  const e = innings.extras;
  const title = `${innings.battingTeam?.name ?? ''}${innings.superOver ? ' (super over)' : ''}`;
  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-baseline justify-between gap-2">
        <CardTitle className="flex items-center gap-2">
          {title}
          {innings.superOver && <Badge variant="secondary">Super over</Badge>}
        </CardTitle>
        <p className="text-sm tabular-nums">
          <span className="text-lg font-semibold">
            {innings.runs}/{innings.wickets}
          </span>{' '}
          <span className="text-muted-foreground">
            ({innings.overs} ov, RR {formatDecimal(innings.runRate)}
            {innings.target ? `, target ${innings.target}` : ''})
          </span>
        </p>
      </CardHeader>
      <CardContent className="space-y-5">
        <DataTable
          caption={`${title} batting`}
          columns={[
            { key: 'b', header: 'Batter', cell: (b) => <PlayerLink player={b.player} /> },
            {
              key: 'd',
              header: 'Dismissal',
              className: 'text-muted-foreground',
              cell: (b) => b.dismissal ?? 'not out',
            },
            {
              key: 'r',
              header: 'R',
              title: 'Runs',
              align: 'right',
              cell: (b) => <span className="font-semibold">{b.runs}</span>,
            },
            { key: 'bl', header: 'B', title: 'Balls', align: 'right', cell: (b) => b.balls },
            { key: '4', header: '4s', align: 'right', cell: (b) => b.fours },
            { key: '6', header: '6s', align: 'right', cell: (b) => b.sixes },
            {
              key: 'sr',
              header: 'SR',
              title: 'Strike rate',
              align: 'right',
              cell: (b) => formatDecimal(b.strikeRate),
            },
          ]}
          rows={innings.batting ?? []}
          rowKey={(b) => b.position}
          emptyTitle="No batting recorded"
        />
        <div className="grid gap-2 text-sm sm:grid-cols-2">
          <p>
            <span className="font-medium">Extras {e?.total ?? 0}</span>{' '}
            <span className="text-muted-foreground">
              (wd {e?.wides ?? 0}, nb {e?.noBalls ?? 0}, b {e?.byes ?? 0}, lb {e?.legByes ?? 0}, pen{' '}
              {e?.penalty ?? 0})
            </span>
          </p>
          {(innings.didNotBat ?? []).length > 0 && (
            <p>
              <span className="font-medium">Did not bat:</span>{' '}
              {(innings.didNotBat ?? []).map((p, i) => (
                <span key={p.id}>
                  {i > 0 && ', '}
                  <PlayerLink player={p} />
                </span>
              ))}
            </p>
          )}
        </div>
        {(innings.fallOfWickets ?? []).length > 0 && (
          <div className="text-sm">
            <p className="font-medium">Fall of wickets</p>
            <ol className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-muted-foreground">
              {(innings.fallOfWickets ?? []).map((f) => (
                <li key={f.wicket}>
                  {f.wicket}-{f.score} ({f.player?.name}, {f.over} ov)
                </li>
              ))}
            </ol>
          </div>
        )}
        <DataTable
          caption={`${title} bowling`}
          columns={[
            { key: 'b', header: 'Bowler', cell: (b) => <PlayerLink player={b.player} /> },
            { key: 'o', header: 'O', title: 'Overs', align: 'right', cell: (b) => b.overs },
            { key: 'm', header: 'M', title: 'Maidens', align: 'right', cell: (b) => b.maidens },
            { key: 'r', header: 'R', title: 'Runs conceded', align: 'right', cell: (b) => b.runsConceded },
            {
              key: 'w',
              header: 'W',
              title: 'Wickets',
              align: 'right',
              cell: (b) => <span className="font-semibold">{b.wickets}</span>,
            },
            {
              key: 'e',
              header: 'Econ',
              title: 'Economy',
              align: 'right',
              cell: (b) => formatDecimal(b.economy),
            },
            { key: '0', header: '0s', title: 'Dot balls', align: 'right', cell: (b) => b.dotBalls },
            { key: 'wd', header: 'WD', title: 'Wides', align: 'right', cell: (b) => b.wides },
            { key: 'nb', header: 'NB', title: 'No-balls', align: 'right', cell: (b) => b.noBalls },
          ]}
          rows={innings.bowling ?? []}
          rowKey={(b) => b.player?.id ?? 0}
          emptyTitle="No bowling recorded"
        />
      </CardContent>
    </Card>
  );
}

export function ScorecardTab() {
  const { match } = useMatchContext();
  const scorecard = useScorecardQuery(match.id);
  return (
    <QueryContent
      query={scorecard}
      loading={<TableSkeleton rows={12} columns={7} />}
      isEmpty={(d) => (d.innings ?? []).length === 0}
      emptyTitle="No scorecard available"
      emptyDescription="The match was abandoned before a ball was bowled."
    >
      {(d) => (
        <div className="space-y-4">
          {(d.innings ?? []).map((innings) => (
            <InningsCard key={innings.inningsNumber} innings={innings} />
          ))}
        </div>
      )}
    </QueryContent>
  );
}

export function ProgressionTab() {
  const { match } = useMatchContext();
  const progression = useMatchProgressionQuery(match.id);
  return (
    <QueryContent
      query={progression}
      loading={<ChartSkeleton height={340} />}
      isEmpty={(d) => (d.innings ?? []).every((i) => (i.overs ?? []).length === 0)}
      emptyTitle="No ball-by-ball data for this match"
    >
      {(d) => {
        // Presentation only: the API's per-over figures, regular innings first.
        const regular: InningsSeries[] = (d.innings ?? [])
          .filter((i) => !i.superOver)
          .map((i) => ({ name: i.team?.code ?? `Innings ${i.inningsNumber}`, overs: i.overs ?? [] }));
        const superOvers = (d.innings ?? []).filter((i) => i.superOver);
        const maxOver = Math.max(0, ...regular.flatMap((i) => i.overs.map((o) => o.over)));
        return (
          <div className="space-y-4">
            <ChartCard
              title="Worm"
              description="Cumulative runs after each over; labelled points mark overs with wickets"
            >
              <WormChart
                innings={regular}
                ariaLabel={`Cumulative runs by over for ${regular.map((i) => i.name).join(' and ')}`}
              />
            </ChartCard>
            <ChartCard
              title="Manhattan"
              description="Runs scored in each over; W labels count the wickets in that over"
            >
              <ManhattanChart
                innings={regular}
                ariaLabel={`Runs per over for ${regular.map((i) => i.name).join(' and ')}`}
              />
            </ChartCard>
            <ChartCard title="Runs per over heat strip">
              <Heatmap
                height={90 + regular.length * 40}
                valueName="Runs"
                xCategories={Array.from({ length: maxOver }, (_, i) => i + 1)}
                yCategories={regular.map((i) => i.name)}
                values={regular.flatMap((inn, y) =>
                  inn.overs.map((o) => [o.over - 1, y, o.runs] as [number, number, number]),
                )}
                ariaLabel="Runs scored in each over, by innings"
              />
            </ChartCard>
            {superOvers.length > 0 && (
              <Card>
                <CardHeader>
                  <CardTitle>Super overs</CardTitle>
                </CardHeader>
                <CardContent>
                  <DataTable
                    caption="Super over totals"
                    columns={[
                      { key: 'n', header: 'Innings', cell: (i) => i.inningsNumber },
                      { key: 't', header: 'Team', cell: (i) => i.team?.name },
                      {
                        key: 'r',
                        header: 'Runs',
                        align: 'right',
                        cell: (i) => formatInteger(i.overs?.at(-1)?.cumulativeRuns),
                      },
                      {
                        key: 'w',
                        header: 'Wickets',
                        align: 'right',
                        cell: (i) => formatInteger(i.overs?.at(-1)?.cumulativeWickets),
                      },
                    ]}
                    rows={superOvers}
                    rowKey={(i) => i.inningsNumber}
                  />
                </CardContent>
              </Card>
            )}
          </div>
        );
      }}
    </QueryContent>
  );
}
