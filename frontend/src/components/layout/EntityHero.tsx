import type { CSSProperties, ReactNode } from 'react';
import { Breadcrumbs, type Crumb } from '@/components/layout/PageHeader';
import { cn } from '@/lib/utils';

export interface HeroStat {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
}

interface EntityHeroProps {
  title: ReactNode;
  breadcrumbs?: Crumb[];
  /** Logo, avatar or a pair of them. */
  media?: ReactNode;
  /** Small line above the title (e.g. "Franchise · Mumbai"). */
  eyebrow?: ReactNode;
  subtitle?: ReactNode;
  badges?: ReactNode;
  /** Headline figures, already formatted by the caller. */
  stats?: HeroStat[];
  actions?: ReactNode;
  /** Brand colours for the accent bar and glow; defaults to the app's brand colours. */
  accent?: { primary: string; secondary: string };
  className?: string;
}

/**
 * The header of every detail page (team, player, match, venue, head-to-head): identity, context and the key
 * figures in one card, tinted with the subject's colours. Text stays on theme colours, so it is readable in both
 * themes whatever the brand colours are.
 */
export function EntityHero({
  title,
  breadcrumbs,
  media,
  eyebrow,
  subtitle,
  badges,
  stats,
  actions,
  accent,
  className,
}: EntityHeroProps) {
  const style = {
    '--hero-from': accent?.primary ?? 'var(--brand-from)',
    '--hero-to': accent?.secondary ?? 'var(--brand-to)',
  } as CSSProperties;
  return (
    <header className={cn('animate-fade-up mb-6 space-y-3', className)}>
      {breadcrumbs && breadcrumbs.length > 0 && <Breadcrumbs items={breadcrumbs} />}
      <div className="relative overflow-hidden rounded-2xl border bg-card shadow-sm" style={style}>
        <div aria-hidden="true" className="hero-bar absolute inset-x-0 top-0 h-1.5" />
        <div aria-hidden="true" className="hero-glow pointer-events-none absolute inset-0" />
        <div className="relative flex flex-col gap-5 p-5 sm:p-6 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex min-w-0 items-center gap-4 sm:gap-5">
            {media && <div className="shrink-0">{media}</div>}
            <div className="min-w-0 space-y-1.5">
              {eyebrow && (
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                  {eyebrow}
                </p>
              )}
              <h1 className="text-2xl font-bold tracking-tight text-balance sm:text-3xl">{title}</h1>
              {subtitle && <div className="text-sm text-muted-foreground">{subtitle}</div>}
              {badges && <div className="flex flex-wrap items-center gap-2 pt-0.5">{badges}</div>}
            </div>
          </div>
          {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </div>
        {stats && stats.length > 0 && (
          <dl
            className={cn(
              'relative grid grid-cols-2 border-t bg-muted/30 sm:grid-cols-3',
              stats.length >= 4 && 'lg:grid-cols-4',
              stats.length >= 5 && 'xl:grid-cols-5',
              stats.length >= 6 && 'xl:grid-cols-6',
            )}
          >
            {stats.map((s) => (
              <div key={s.label} className="-mb-px -mr-px border-b border-r px-5 py-3.5 sm:px-6">
                <dt className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  {s.label}
                </dt>
                <dd className="mt-1 text-xl font-bold tabular-nums tracking-tight sm:text-2xl">{s.value}</dd>
                {s.hint && <dd className="mt-0.5 truncate text-xs text-muted-foreground">{s.hint}</dd>}
              </div>
            ))}
          </dl>
        )}
      </div>
    </header>
  );
}
