export interface NavSection {
  path: string;
  label: string;
  description: string;
  plannedFor: string;
}

/** Top-level sections. Pages are placeholders until their roadmap version lands. */
export const NAV_SECTIONS: readonly NavSection[] = [
  { path: '/teams', label: 'Teams', description: 'Franchise records, season-by-season results and top performers.', plannedFor: 'V1' },
  { path: '/players', label: 'Players', description: 'Career and season batting and bowling statistics.', plannedFor: 'V1' },
  { path: '/matches', label: 'Matches', description: 'Results, scorecards and match progression.', plannedFor: 'V1' },
  { path: '/venues', label: 'Venues', description: 'Scoring patterns, toss impact and bat-first win rates.', plannedFor: 'V1' },
  { path: '/seasons', label: 'Seasons', description: 'Points tables, champions and season leaders.', plannedFor: 'V1' },
  { path: '/head-to-head', label: 'Head to Head', description: 'Team vs team records across seasons.', plannedFor: 'V1.1' },
  { path: '/compare', label: 'Compare', description: 'Player vs player comparison and batter vs bowler matchups.', plannedFor: 'V1.1' },
];
