import { CalendarDays, MapPin } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import type { MatchSummary } from '@/api/matchesApi';
import type { Team } from '@/api/teamsApi';
import type { Venue } from '@/api/venuesApi';
import { PlayerAvatar } from '@/components/media/PlayerAvatar';
import { TeamLogo } from '@/components/media/TeamLogo';
import { Badge } from '@/components/ui/badge';
import { teamBranding } from '@/config/media/teamBranding';
import { formatDate, formatInteger, formatPercent } from '@/lib/format';
import { cn } from '@/lib/utils';

const cardLink =
  'group card-interactive relative flex flex-col overflow-hidden rounded-xl border bg-card text-card-foreground shadow-sm';

function AccentBar({ code, name }: { code?: string; name?: string }) {
  const brand = teamBranding(code, name);
  return (
    <span
      aria-hidden="true"
      className="absolute inset-x-0 top-0 h-1"
      style={{ background: `linear-gradient(90deg, ${brand.primary}, ${brand.secondary})` }}
    />
  );
}

function MiniStat({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="rounded-lg bg-muted/60 px-2 py-1.5">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="text-sm font-bold tabular-nums">{value}</p>
    </div>
  );
}

/** A franchise tile: crest, identity, all-time record and a win-rate bar. Values come from the API as-is. */
export function TeamCard({ team }: { team: Team }) {
  const r = team.record;
  const winPct = r?.winPercentage;
  const brand = teamBranding(team.code, team.name);
  return (
    <Link to={`/teams/${team.id}`} className={cn(cardLink, 'p-4 pt-5')}>
      <AccentBar code={team.code} name={team.name} />
      <div className="flex items-start gap-3">
        <TeamLogo team={team} size="lg" className="transition-transform duration-300 group-hover:scale-105" />
        <div className="min-w-0 flex-1">
          <p className="font-semibold leading-tight">{team.name}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {team.code}
            {team.homeCity && ` · ${team.homeCity}`}
          </p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {team.firstSeason}–{team.active ? 'present' : team.lastSeason}
          </p>
        </div>
        {!team.active && <Badge variant="outline">Defunct</Badge>}
      </div>
      <div className="mt-4 grid grid-cols-3 gap-2 text-center">
        <MiniStat label="Played" value={formatInteger(r?.played)} />
        <MiniStat label="Won" value={formatInteger(r?.won)} />
        <MiniStat label="Win %" value={formatPercent(winPct)} />
      </div>
      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
        <div
          className="h-full rounded-full"
          style={{ width: `${Math.min(100, winPct ?? 0)}%`, background: brand.primary }}
        />
      </div>
      {team.latestSeason && (
        <p className="mt-3 text-xs text-muted-foreground">
          {team.latestSeason.season}: {team.latestSeason.record?.won ?? 0} won,{' '}
          {team.latestSeason.record?.lost ?? 0} lost
        </p>
      )}
    </Link>
  );
}

interface PlayerCardProps {
  player: { id: number; name?: string; sourceId?: string } | null | undefined;
  team?: { code?: string; name?: string };
  /** The headline figure, formatted, e.g. "741". */
  value: ReactNode;
  /** What the figure is, e.g. "Most runs". */
  label: string;
  hint?: ReactNode;
}

/** A player leader tile (e.g. most runs in a season): avatar, name and one prominent figure. */
export function PlayerCard({ player, team, value, label, hint }: PlayerCardProps) {
  const body = (
    <>
      <AccentBar code={team?.code} name={player?.name} />
      <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</p>
      <div className="mt-2 flex items-center gap-3">
        <PlayerAvatar player={player} teamCode={team?.code} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">{player?.name ?? '–'}</p>
          {hint && <p className="truncate text-xs text-muted-foreground">{hint}</p>}
        </div>
        <p className="text-2xl font-bold tabular-nums tracking-tight">{value}</p>
      </div>
    </>
  );
  return player ? (
    <Link to={`/players/${player.id}`} className={cn(cardLink, 'p-4 pt-5')}>
      {body}
    </Link>
  ) : (
    <div className={cn(cardLink, 'p-4 pt-5')}>{body}</div>
  );
}

/** A compact match tile: both sides with crest and score, the winner emphasised, result and venue. */
export function MatchCard({ match }: { match: MatchSummary }) {
  const regular = (match.innings ?? []).filter((i) => !i.superOver);
  const sides = [match.team1, match.team2].map((side) => {
    const team = side?.team;
    const innings = regular.find((i) => i.team?.id === team?.id);
    return { team, name: side?.nameAtTheTime ?? team?.name, innings, won: match.winner?.id === team?.id };
  });
  const decided = match.winner !== undefined;
  return (
    <Link
      to={`/matches/${match.id}`}
      className={cn(cardLink, 'p-4')}
      aria-label={`${sides[0]?.name ?? ''} v ${sides[1]?.name ?? ''}, ${formatDate(match.date)}`}
    >
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <CalendarDays aria-hidden="true" className="size-3.5" />
          {formatDate(match.date)}
        </span>
        {match.stage && match.stage !== 'League' ? (
          <Badge variant="default" className="bg-brand-gradient text-white">
            {match.stage}
          </Badge>
        ) : (
          <span>
            {match.season}
            {match.matchNumber ? ` · Match ${match.matchNumber}` : ''}
          </span>
        )}
      </div>
      <ul className="mt-3 space-y-2">
        {sides.map((s, i) => (
          <li key={s.team?.id ?? i} className="flex items-center gap-3">
            <TeamLogo team={s.team} size="sm" />
            <span
              className={cn(
                'min-w-0 flex-1 truncate text-sm',
                s.won ? 'font-semibold' : decided ? 'text-muted-foreground' : '',
              )}
            >
              {s.name ?? '–'}
            </span>
            <span className={cn('text-sm tabular-nums', s.won ? 'font-bold' : 'text-muted-foreground')}>
              {s.innings ? `${s.innings.runs}/${s.innings.wickets}` : '–'}
              {s.innings?.overs && (
                <span className="ml-1 text-xs font-normal text-muted-foreground">({s.innings.overs})</span>
              )}
            </span>
          </li>
        ))}
      </ul>
      <p className="mt-3 text-sm font-medium text-primary">{match.resultText ?? '–'}</p>
      {match.venue && (
        <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
          <MapPin aria-hidden="true" className="size-3.5 shrink-0" />
          <span className="truncate">{match.venue.name}</span>
        </p>
      )}
    </Link>
  );
}

/** A venue tile: name, city and how much cricket it has hosted. */
export function VenueCard({ venue }: { venue: Venue }) {
  return (
    <Link to={`/venues/${venue.id}`} className={cn(cardLink, 'p-4')}>
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className="grid size-10 shrink-0 place-items-center rounded-lg bg-brand-gradient text-white shadow-sm"
        >
          <MapPin className="size-5" />
        </span>
        <div className="min-w-0">
          <p className="font-semibold leading-tight">{venue.name}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{venue.city ?? '–'}</p>
        </div>
      </div>
      <div className="mt-4 flex items-end justify-between text-xs text-muted-foreground">
        <span>
          <span className="text-lg font-bold tabular-nums text-foreground">
            {formatInteger(venue.matches)}
          </span>{' '}
          matches
        </span>
        <span>
          {venue.firstSeason}
          {venue.lastSeason !== venue.firstSeason && `–${venue.lastSeason}`}
        </span>
      </div>
    </Link>
  );
}

/** Responsive grid for entity cards. */
export function CardGrid({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <div className={cn('grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4', className)}>{children}</div>
  );
}
