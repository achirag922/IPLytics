import { useId, useState } from 'react';
import { teamBranding } from '@/config/media/teamBranding';
import { cn } from '@/lib/utils';

const SIZES = { xs: 20, sm: 28, md: 40, lg: 56, xl: 80 } as const;
export type MediaSize = keyof typeof SIZES;

interface TeamLogoProps {
  team: { code?: string; name?: string } | null | undefined;
  size?: MediaSize;
  /** Accessible name; omitted, the logo is decorative (the team name is next to it). */
  label?: string;
  className?: string;
}

/**
 * A franchise logo: the configured image when there is one, otherwise an original shield crest in the team's
 * colours. A broken image falls back to the crest.
 */
export function TeamLogo({ team, size = 'md', label, className }: TeamLogoProps) {
  const px = SIZES[size];
  const brand = teamBranding(team?.code, team?.name);
  const [failed, setFailed] = useState<string>();

  if (brand.logoUrl && failed !== brand.logoUrl) {
    return (
      <img
        src={brand.logoUrl}
        alt={label ?? ''}
        width={px}
        height={px}
        loading="lazy"
        decoding="async"
        onError={() => setFailed(brand.logoUrl)}
        className={cn('shrink-0 object-contain', className)}
        style={{ width: px, height: px }}
      />
    );
  }
  return <Crest code={team?.code} brand={brand} px={px} label={label} className={className} />;
}

function Crest({
  code,
  brand,
  px,
  label,
  className,
}: {
  code: string | undefined;
  brand: ReturnType<typeof teamBranding>;
  px: number;
  label?: string;
  className?: string;
}) {
  const id = useId();
  const text = (code ?? '?').slice(0, 4);
  return (
    <svg
      viewBox="0 0 40 44"
      width={px}
      height={px * 1.1}
      className={cn('shrink-0 drop-shadow-sm', className)}
      data-testid="team-crest"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <defs>
        <linearGradient id={`${id}-fill`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={brand.primary} />
          <stop offset="1" stopColor={brand.primary} stopOpacity="0.82" />
        </linearGradient>
      </defs>
      <path
        d="M20 1.5 37.5 7.5V21c0 10.6-7.4 17.9-17.5 21.5C9.9 38.9 2.5 31.6 2.5 21V7.5Z"
        fill={`url(#${id}-fill)`}
        stroke={brand.secondary}
        strokeWidth="2.5"
      />
      <path d="M6 10.5 20 6l14 4.5" fill="none" stroke={brand.secondary} strokeWidth="1.2" opacity="0.7" />
      <text
        x="20"
        y="26"
        textAnchor="middle"
        dominantBaseline="middle"
        fill={brand.onPrimary}
        fontSize={text.length > 3 ? 8.5 : text.length > 2 ? 10.5 : 13}
        fontWeight="800"
        fontFamily="Inter, system-ui, sans-serif"
        letterSpacing="0.5"
      >
        {text}
      </text>
    </svg>
  );
}
