import { Link } from 'react-router';
import { TeamLogo } from '@/components/media/TeamLogo';
import { teamBranding } from '@/config/media/teamBranding';
import { formatInteger } from '@/lib/format';

interface Side {
  team?: { id: number; code?: string; name?: string };
  wins: number;
}

/**
 * Two franchises face to face: crests, win counts and a bar split by each side's share of the wins.
 * The counts come from the API; the bar widths are presentation of those counts.
 */
export function RivalryBanner({ a, b, matches }: { a: Side; b: Side; matches: number }) {
  const total = a.wins + b.wins;
  const shareA = total === 0 ? 50 : (a.wins * 100) / total;
  const brandA = teamBranding(a.team?.code, a.team?.name);
  const brandB = teamBranding(b.team?.code, b.team?.name);
  return (
    <section
      aria-label="Rivalry summary"
      className="animate-fade-up relative overflow-hidden rounded-2xl border bg-card p-5 shadow-sm sm:p-6"
    >
      <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 sm:gap-6">
        <SideBlock side={a} align="start" />
        <div className="text-center">
          <p className="text-3xl font-extrabold tabular-nums tracking-tight sm:text-5xl">
            {formatInteger(a.wins)}
            <span className="mx-2 text-muted-foreground sm:mx-3">–</span>
            {formatInteger(b.wins)}
          </p>
          <p className="mt-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
            {formatInteger(matches)} meetings
          </p>
        </div>
        <SideBlock side={b} align="end" />
      </div>
      <div className="mt-5 flex h-2.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
        <div style={{ width: `${shareA}%`, background: brandA.primary }} />
        <div style={{ width: `${100 - shareA}%`, background: brandB.primary }} />
      </div>
    </section>
  );
}

function SideBlock({ side, align }: { side: Side; align: 'start' | 'end' }) {
  return (
    <div
      className={`flex min-w-0 flex-col gap-2 ${align === 'end' ? 'items-end text-right' : 'items-start'}`}
    >
      <TeamLogo team={side.team} size="lg" />
      {side.team ? (
        <Link
          to={`/teams/${side.team.id}`}
          className="max-w-full truncate text-sm font-semibold hover:underline sm:text-base"
        >
          {side.team.name}
        </Link>
      ) : (
        <span>–</span>
      )}
    </div>
  );
}
