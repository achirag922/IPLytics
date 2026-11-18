import { Link } from 'react-router';
import { PlayerAvatar } from '@/components/media/PlayerAvatar';
import { TeamLogo } from '@/components/media/TeamLogo';
import { formatText } from '@/lib/format';

const linkClass = 'font-medium text-primary underline-offset-4 hover:underline';

interface Ref {
  id: number;
  name?: string;
}

/** Link to a player; with `avatar`, preceded by the player's photo or initials (coloured by `teamCode`). */
export function PlayerLink({
  player,
  avatar = false,
  teamCode,
}: {
  player: (Ref & { sourceId?: string }) | null | undefined;
  avatar?: boolean;
  teamCode?: string;
}) {
  if (!player) return <>{formatText(undefined)}</>;
  const link = (
    <Link to={`/players/${player.id}`} className={linkClass}>
      {formatText(player.name)}
    </Link>
  );
  if (!avatar) return link;
  return (
    <span className="inline-flex items-center gap-2">
      <PlayerAvatar player={player} teamCode={teamCode} size="sm" />
      {link}
    </span>
  );
}

/** Link to a team; with `logo`, preceded by its crest. */
export function TeamLink({
  team,
  short = false,
  logo = false,
}: {
  team: (Ref & { code?: string }) | null | undefined;
  short?: boolean;
  logo?: boolean;
}) {
  if (!team) return <>{formatText(undefined)}</>;
  const link = (
    <Link to={`/teams/${team.id}`} className={linkClass} title={short ? team.name : undefined}>
      {formatText(short ? team.code : team.name)}
    </Link>
  );
  if (!logo) return link;
  return (
    <span className="inline-flex items-center gap-1.5">
      <TeamLogo team={team} size="xs" />
      {link}
    </span>
  );
}

export function VenueLink({ venue }: { venue: (Ref & { city?: string }) | null | undefined }) {
  if (!venue) return <>{formatText(undefined)}</>;
  return (
    <Link to={`/venues/${venue.id}`} className={linkClass}>
      {formatText(venue.name)}
    </Link>
  );
}
