import type { BattingFirstInsights, PhaseInsights, PhaseRow, TossInsights } from '@/api/analyticsApi';
import { StatCard, StatGrid } from '@/components/cards/StatCard';
import { BarChart, ChartCard, LineChart } from '@/components/charts';
import { DataTable, type Column } from '@/components/tables/DataTable';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDecimal, formatInteger, formatPercent } from '@/lib/format';

/** Called with a season clicked in a by-season chart (cross-filter the page to it). */
export type SeasonSelect = (season: number) => void;

import { PHASE_LABEL } from '@/lib/phases';
const phaseLabel = (p: PhaseRow) => PHASE_LABEL[p.phase ?? ''] ?? p.phase ?? '';

const phaseColumns: Column<PhaseRow>[] = [
  { key: 'phase', header: 'Phase', cell: phaseLabel },
  { key: 'inn', header: 'Inns', title: 'Innings', align: 'right', cell: (p) => formatInteger(p.innings) },
  { key: 'runs', header: 'Runs', align: 'right', cell: (p) => formatInteger(p.runs) },
  { key: 'overs', header: 'Overs', align: 'right', cell: (p) => p.overs ?? '–' },
  { key: 'rr', header: 'RR', title: 'Run rate', align: 'right', cell: (p) => formatDecimal(p.runRate) },
  { key: 'rpi', header: 'Runs/inns', align: 'right', cell: (p) => formatDecimal(p.runsPerInnings) },
  { key: 'w', header: 'Wkts', title: 'Wickets', align: 'right', cell: (p) => formatInteger(p.wickets) },
  { key: 'wpi', header: 'Wkts/inns', align: 'right', cell: (p) => formatDecimal(p.wicketsPerInnings) },
  { key: '4', header: '4s', align: 'right', cell: (p) => formatInteger(p.fours) },
  { key: '6', header: '6s', align: 'right', cell: (p) => formatInteger(p.sixes) },
  { key: 'b', header: 'Boundary %', align: 'right', cell: (p) => formatPercent(p.boundaryPercentage) },
  { key: 'd', header: 'Dot %', align: 'right', cell: (p) => formatPercent(p.dotBallPercentage) },
];

export function PhaseTable({
  rows,
  caption,
  exportFileName,
}: {
  rows: PhaseRow[];
  caption: string;
  exportFileName?: string;
}) {
  return (
    <DataTable
      caption={caption}
      columns={phaseColumns}
      rows={rows}
      rowKey={(p) => p.phase ?? ''}
      exportFileName={exportFileName}
    />
  );
}

interface PhaseSectionProps {
  data: PhaseInsights;
  /** "MI batting" / "MI bowling" labels when a team is selected. */
  battingLabel?: string;
  bowlingLabel?: string;
  onSeasonSelect?: SeasonSelect;
  subject: string;
  /** Use the labels as series names as given (no "scored" / "conceded" suffix). */
  plainLabels?: boolean;
}

/** Phase tables, the run rate by phase (batting vs bowling with a team) and the phase trend by season. */
export function PhaseSection({
  data,
  battingLabel = 'Scoring',
  bowlingLabel,
  onSeasonSelect,
  subject,
  plainLabels = false,
}: PhaseSectionProps) {
  const batting = data.batting ?? [];
  const bowling = data.bowling;
  const seasons = data.bySeason ?? [];
  return (
    <div className="space-y-4">
      <div className="grid gap-4 xl:grid-cols-2">
        <ChartCard
          title="Run rate by phase"
          description={bowling ? `${battingLabel} vs ${bowlingLabel}` : undefined}
        >
          <BarChart
            height={260}
            categories={batting.map(phaseLabel)}
            series={[
              {
                name: bowling ? (plainLabels ? battingLabel : `${battingLabel} (scored)`) : 'Run rate',
                data: batting.map((p) => p.runRate),
              },
              ...(bowling
                ? [
                    {
                      name: plainLabels ? (bowlingLabel ?? '') : `${bowlingLabel} (conceded)`,
                      data: bowling.map((p) => p.runRate),
                    },
                  ]
                : []),
            ]}
            ariaLabel={`Run rate by phase, ${subject}`}
          />
        </ChartCard>
        <ChartCard
          title="Phase run rate by season"
          description={onSeasonSelect ? 'Click a season to focus on it' : undefined}
        >
          <LineChart
            height={260}
            zoom={seasons.length > 12}
            categories={seasons.map((s) => s.season)}
            series={[
              { name: 'Powerplay', data: seasons.map((s) => s.powerplayRunRate) },
              { name: 'Middle', data: seasons.map((s) => s.middleRunRate) },
              { name: 'Death', data: seasons.map((s) => s.deathRunRate) },
            ]}
            ariaLabel={`Phase run rate by season, ${subject}`}
            onItemClick={onSeasonSelect ? (_, season) => onSeasonSelect(Number(season)) : undefined}
          />
        </ChartCard>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>{bowling ? battingLabel : 'By phase'}</CardTitle>
        </CardHeader>
        <CardContent>
          <PhaseTable
            rows={batting}
            caption={`Phases, ${subject}${bowling ? ', batting' : ''}`}
            exportFileName={`phases ${subject}`}
          />
        </CardContent>
      </Card>
      {bowling && (
        <Card>
          <CardHeader>
            <CardTitle>{bowlingLabel}</CardTitle>
          </CardHeader>
          <CardContent>
            <PhaseTable
              rows={bowling}
              caption={`Phases, ${subject}, bowling`}
              exportFileName={`phases bowling ${subject}`}
            />
          </CardContent>
        </Card>
      )}
    </div>
  );
}

/** Toss winner results: KPIs, by decision, by season and, with a team, its own toss record. */
export function TossSection({
  data,
  onSeasonSelect,
  subject,
}: {
  data: TossInsights;
  onSeasonSelect?: SeasonSelect;
  subject: string;
}) {
  const o = data.overall;
  const decisions = data.byDecision ?? [];
  const seasons = data.bySeason ?? [];
  const team = data.team;
  return (
    <div className="space-y-4">
      <StatGrid>
        <StatCard
          label="Matches with a toss"
          value={formatInteger(o?.matches)}
          hint={`${formatInteger(o?.decided)} with a result`}
        />
        <StatCard
          label="Toss winner won"
          value={formatPercent(o?.tossWinnerWinPercentage)}
          hint={`${formatInteger(o?.tossWinnerWins)} matches`}
        />
        <StatCard
          label="Chose to bat"
          value={formatInteger(o?.choseToBatFirst)}
          hint={`${formatInteger((o?.matches ?? 0) - (o?.choseToBatFirst ?? 0))} chose to field`}
        />
        {team && (
          <StatCard
            label={`${team.team?.code ?? 'Team'} won the toss`}
            value={formatPercent(team.tossWinPercentage)}
            hint={`${formatInteger(team.tossesWon)} of ${formatInteger(team.matches)} matches`}
          />
        )}
      </StatGrid>
      {team && (
        <Card>
          <CardHeader>
            <CardTitle>{team.team?.name} and the toss</CardTitle>
          </CardHeader>
          <CardContent>
            <DataTable
              caption={`${team.team?.name} results after winning and losing the toss`}
              exportFileName={`toss ${team.team?.code ?? ''}`}
              columns={[
                {
                  key: 'when',
                  header: 'Toss',
                  cell: (r: { label: string; decided: number; wins: number; pct?: number }) => r.label,
                },
                { key: 'm', header: 'Matches', align: 'right', cell: (r) => formatInteger(r.decided) },
                { key: 'w', header: 'Won', align: 'right', cell: (r) => formatInteger(r.wins) },
                { key: 'p', header: 'Win %', align: 'right', cell: (r) => formatPercent(r.pct) },
              ]}
              rows={[
                {
                  label: 'Won the toss',
                  decided: team.decidedAfterTossWin,
                  wins: team.winsAfterTossWin,
                  pct: team.winPercentageAfterTossWin,
                },
                {
                  label: 'Lost the toss',
                  decided: team.decidedAfterTossLoss,
                  wins: team.winsAfterTossLoss,
                  pct: team.winPercentageAfterTossLoss,
                },
              ]}
              rowKey={(r) => r.label}
            />
            <p className="mt-2 text-sm text-muted-foreground">
              After winning the toss: chose to bat {formatInteger(team.choseToBat)} times, to field{' '}
              {formatInteger(team.choseToField)}.
            </p>
          </CardContent>
        </Card>
      )}
      <div className="grid gap-4 xl:grid-cols-2">
        <ChartCard title="Toss decision and result" description="Matches and toss-winner win % by decision">
          <BarChart
            height={260}
            categories={decisions.map((d) => (d.decision === 'bat' ? 'Chose to bat' : 'Chose to field'))}
            series={[
              { name: 'Matches', data: decisions.map((d) => d.result?.matches) },
              { name: 'Toss winner won %', data: decisions.map((d) => d.result?.tossWinnerWinPercentage) },
            ]}
            ariaLabel={`Toss decisions and toss-winner win percentage, ${subject}`}
          />
        </ChartCard>
        <ChartCard
          title="Toss winner win % by season"
          description={onSeasonSelect ? 'Click a season to focus on it' : undefined}
        >
          <LineChart
            height={260}
            zoom={seasons.length > 12}
            categories={seasons.map((s) => s.season)}
            series={[
              { name: 'Toss winner won %', data: seasons.map((s) => s.result?.tossWinnerWinPercentage) },
            ]}
            ariaLabel={`Toss-winner win percentage by season, ${subject}`}
            onItemClick={onSeasonSelect ? (_, season) => onSeasonSelect(Number(season)) : undefined}
          />
        </ChartCard>
      </div>
    </div>
  );
}

const bandLabel = (from?: number, to?: number) =>
  from == null ? `Under ${(to ?? 0) + 1}` : to == null ? `${from}+` : `${from}–${to}`;

/** Batting first vs chasing: KPIs, wins by season and chase success by target. */
export function BattingFirstSection({
  data,
  onSeasonSelect,
  subject,
}: {
  data: BattingFirstInsights;
  onSeasonSelect?: SeasonSelect;
  subject: string;
}) {
  const o = data.overall;
  const seasons = data.bySeason ?? [];
  const bands = data.byTarget ?? [];
  return (
    <div className="space-y-4">
      <StatGrid>
        <StatCard
          label="Decisive matches"
          value={formatInteger(o?.decisiveMatches)}
          hint="Won by runs or wickets"
        />
        <StatCard
          label="Batting first won"
          value={formatPercent(o?.battingFirstWinPercentage)}
          hint={`${formatInteger(o?.battingFirstWins)} matches`}
        />
        <StatCard
          label="Chasing won"
          value={formatPercent(o?.chasingWinPercentage)}
          hint={`${formatInteger(o?.chasingWins)} matches`}
        />
        <StatCard
          label="Avg 1st-innings score"
          value={formatDecimal(o?.averageFirstInningsScore)}
          hint="No D/L matches"
        />
      </StatGrid>
      <div className="grid gap-4 xl:grid-cols-2">
        <ChartCard
          title="Wins batting first vs chasing"
          description={onSeasonSelect ? 'Click a season to focus on it' : undefined}
        >
          <BarChart
            height={260}
            zoom={seasons.length > 12}
            categories={seasons.map((s) => s.season)}
            series={[
              { name: 'Batting first', data: seasons.map((s) => s.result?.battingFirstWins), stack: 'w' },
              { name: 'Chasing', data: seasons.map((s) => s.result?.chasingWins), stack: 'w' },
            ]}
            ariaLabel={`Wins batting first and chasing by season, ${subject}`}
            onItemClick={onSeasonSelect ? (_, season) => onSeasonSelect(Number(season)) : undefined}
          />
        </ChartCard>
        <ChartCard
          title="Chase success by target"
          description="Successful chases, % of matches, by target set"
        >
          <BarChart
            height={260}
            categories={bands.map((b) => bandLabel(b.from, b.to))}
            series={[
              { name: 'Chase success %', data: bands.map((b) => b.successPercentage), color: '#059669' },
            ]}
            xAxisName="Target"
            ariaLabel={`Chase success percentage by target, ${subject}`}
          />
        </ChartCard>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Chases by target</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            caption={`Chases by target, ${subject}`}
            exportFileName={`chases by target ${subject}`}
            columns={[
              { key: 't', header: 'Target', cell: (b) => bandLabel(b.from, b.to) },
              { key: 'm', header: 'Matches', align: 'right', cell: (b) => formatInteger(b.matches) },
              {
                key: 'c',
                header: 'Chased',
                align: 'right',
                cell: (b) => formatInteger(b.chasedSuccessfully),
              },
              {
                key: 'p',
                header: 'Success %',
                align: 'right',
                cell: (b) => formatPercent(b.successPercentage),
              },
            ]}
            rows={bands}
            rowKey={(b) => `${b.from}-${b.to}`}
          />
        </CardContent>
      </Card>
    </div>
  );
}
