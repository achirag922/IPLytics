import { Link } from 'react-router';
import type { MatchSummary } from '@/api/matchesApi';
import { TeamLink, VenueLink } from '@/components/cards/EntityLinks';
import type { Column } from '@/components/tables/DataTable';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/format';

/** "MI 182/5 · CSK 180/8" from the innings scores (super overs left out). */
function scoreLine(match: MatchSummary): string {
  return (match.innings ?? [])
    .filter((i) => !i.superOver)
    .map((i) => `${i.team?.code ?? ''} ${i.runs}/${i.wickets}`)
    .join(' · ');
}

/** Match list columns, shared by the matches page and the head-to-head recent meetings. */
export const matchColumns: Column<MatchSummary>[] = [
  {
    key: 'date',
    header: 'Date',
    cell: (m) => (
      <Link to={`/matches/${m.id}`} className="font-medium text-primary hover:underline">
        {formatDate(m.date)}
      </Link>
    ),
  },
  { key: 'season', header: 'Season', cell: (m) => m.season },
  { key: 'team1', header: 'Team 1', cell: (m) => <TeamLink team={m.team1?.team} short /> },
  { key: 'team2', header: 'Team 2', cell: (m) => <TeamLink team={m.team2?.team} short /> },
  { key: 'winner', header: 'Winner', cell: (m) => (m.winner ? <TeamLink team={m.winner} short /> : '–') },
  {
    key: 'venue',
    header: 'Venue',
    className: 'max-w-64 truncate',
    cell: (m) => <VenueLink venue={m.venue} />,
  },
  {
    key: 'result',
    header: 'Result',
    cell: (m) => (
      <div className="flex flex-col">
        <span className="flex items-center gap-2">
          {m.resultText}
          {m.stage && m.stage !== 'League' && <Badge variant="outline">{m.stage}</Badge>}
        </span>
        <span className="text-xs text-muted-foreground">{scoreLine(m)}</span>
      </div>
    ),
  },
];
