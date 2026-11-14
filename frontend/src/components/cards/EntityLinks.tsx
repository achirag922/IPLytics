import { Link } from 'react-router';
import { formatText } from '@/lib/format';

const linkClass = 'font-medium text-primary underline-offset-4 hover:underline';

interface Ref {
  id: number;
  name?: string;
}

export function PlayerLink({ player }: { player: Ref | null | undefined }) {
  if (!player) return <>{formatText(undefined)}</>;
  return (
    <Link to={`/players/${player.id}`} className={linkClass}>
      {formatText(player.name)}
    </Link>
  );
}

export function TeamLink({
  team,
  short = false,
}: {
  team: (Ref & { code?: string }) | null | undefined;
  short?: boolean;
}) {
  if (!team) return <>{formatText(undefined)}</>;
  return (
    <Link to={`/teams/${team.id}`} className={linkClass} title={short ? team.name : undefined}>
      {formatText(short ? team.code : team.name)}
    </Link>
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
