import { useState } from 'react';
import { playerImageUrl, initials } from '@/config/media/playerImages';
import { teamBranding } from '@/config/media/teamBranding';
import { cn } from '@/lib/utils';
import type { MediaSize } from './TeamLogo';

const SIZES: Record<MediaSize, { px: number; text: string }> = {
  xs: { px: 20, text: 'text-[9px]' },
  sm: { px: 28, text: 'text-[11px]' },
  md: { px: 40, text: 'text-sm' },
  lg: { px: 56, text: 'text-lg' },
  xl: { px: 96, text: 'text-3xl' },
};

interface PlayerAvatarProps {
  player: { name?: string; sourceId?: string } | null | undefined;
  /** Franchise code used to colour the initials fallback. */
  teamCode?: string;
  size?: MediaSize;
  className?: string;
}

/**
 * A player photo (lazy-loaded, from the image configuration) with an initials avatar in the team's colours when no
 * photo is configured or it fails to load. Decorative: the player's name is always shown next to it.
 */
export function PlayerAvatar({ player, teamCode, size = 'md', className }: PlayerAvatarProps) {
  const { px, text } = SIZES[size];
  const url = playerImageUrl(player?.sourceId);
  const [failed, setFailed] = useState<string>();
  const brand = teamBranding(teamCode, player?.name);
  const box = cn(
    'relative inline-grid shrink-0 place-items-center overflow-hidden rounded-full ring-2 ring-background',
    className,
  );

  if (url && failed !== url) {
    return (
      <span className={cn(box, 'bg-muted')} style={{ width: px, height: px }} aria-hidden="true">
        <img
          src={url}
          alt=""
          width={px}
          height={px}
          loading="lazy"
          decoding="async"
          onError={() => setFailed(url)}
          className="size-full object-cover object-top"
        />
      </span>
    );
  }
  return (
    <span
      aria-hidden="true"
      data-testid="avatar-fallback"
      className={cn(box, 'font-semibold tracking-wide', text)}
      style={{
        width: px,
        height: px,
        color: brand.onPrimary,
        background: `linear-gradient(135deg, ${brand.primary}, ${brand.secondary})`,
      }}
    >
      {initials(player?.name)}
    </span>
  );
}
