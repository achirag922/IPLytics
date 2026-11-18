import type { MatchSummary } from '@/api/matchesApi';
import type { PlayerListItem } from '@/api/playersApi';
import type { CsvColumn } from '@/lib/csv';

/** CSV columns for "export all" of the paged lists: raw API values, so numbers stay numbers in a spreadsheet. */
export const PLAYER_CSV_COLUMNS: CsvColumn<PlayerListItem>[] = [
  { header: 'Player', value: (p) => p.name },
  { header: 'Player id', value: (p) => p.id },
  { header: 'Latest team', value: (p) => p.team?.code },
  { header: 'First season', value: (p) => p.firstSeason },
  { header: 'Last season', value: (p) => p.lastSeason },
  { header: 'Matches', value: (p) => p.matches },
  { header: 'Batting innings', value: (p) => p.battingInnings },
  { header: 'Runs', value: (p) => p.runs },
  { header: 'Batting average', value: (p) => p.battingAverage },
  { header: 'Strike rate', value: (p) => p.strikeRate },
  { header: 'Bowling innings', value: (p) => p.bowlingInnings },
  { header: 'Wickets', value: (p) => p.wickets },
  { header: 'Economy', value: (p) => p.economy },
];

const score = (m: MatchSummary, index: number) => {
  const innings = (m.innings ?? []).filter((i) => !i.superOver)[index];
  return innings ? `${innings.runs}/${innings.wickets} (${innings.overs})` : undefined;
};

export const MATCH_CSV_COLUMNS: CsvColumn<MatchSummary>[] = [
  { header: 'Date', value: (m) => m.date },
  { header: 'Season', value: (m) => m.season },
  { header: 'Stage', value: (m) => m.stage },
  { header: 'Match id', value: (m) => m.id },
  { header: 'Team 1', value: (m) => m.team1?.nameAtTheTime ?? m.team1?.team?.name },
  { header: 'Team 2', value: (m) => m.team2?.nameAtTheTime ?? m.team2?.team?.name },
  { header: 'First innings', value: (m) => score(m, 0) },
  { header: 'Second innings', value: (m) => score(m, 1) },
  { header: 'Winner', value: (m) => m.winner?.name },
  { header: 'Result', value: (m) => m.resultText },
  { header: 'Venue', value: (m) => m.venue?.name },
  { header: 'City', value: (m) => m.venue?.city },
];
