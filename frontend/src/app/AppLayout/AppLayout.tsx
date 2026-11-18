import { CalendarRange, Menu, Monitor, Moon, Sun, X } from 'lucide-react';
import { Suspense, useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { useSeasonsQuery } from '@/hooks/useSeasonQueries';
import { PageSkeleton } from '@/components/loading/Skeletons';
import { Button } from '@/components/ui/button';
import { useTheme, type ThemePreference } from '@/lib/theme';
import { cn } from '@/lib/utils';
import { NAVIGATION } from './navigation';

function Brand() {
  return (
    <Link
      to="/"
      className="group flex items-center gap-2.5 px-3 text-lg font-bold tracking-tight text-sidebar-accent-foreground"
    >
      <span
        aria-hidden="true"
        className="grid size-8 place-items-center rounded-lg bg-brand-gradient text-[11px] font-extrabold text-white shadow-md transition-transform group-hover:rotate-[-6deg]"
      >
        IPL
      </span>
      <span>
        IPL<span className="text-brand-gradient">ytics</span>
      </span>
    </Link>
  );
}

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Main" className="flex flex-col gap-5">
      {NAVIGATION.map((group, index) => (
        <div key={group.label ?? index}>
          {group.label && (
            <p className="mb-1 px-3 text-[11px] font-semibold uppercase tracking-wider text-sidebar-foreground/60">
              {group.label}
            </p>
          )}
          <ul className="flex flex-col gap-0.5">
            {group.items.map(({ label, to, icon: Icon }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={to === '/'}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    cn(
                      'relative flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-sidebar-accent text-sidebar-accent-foreground shadow-sm before:absolute before:inset-y-1.5 before:left-0 before:w-1 before:rounded-full before:bg-brand-gradient'
                        : 'text-sidebar-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground',
                    )
                  }
                >
                  <Icon aria-hidden="true" className="size-4" />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}

function SidebarFooter() {
  return (
    <p className="mt-auto px-3 text-[11px] leading-relaxed text-sidebar-foreground/60">
      Ball-by-ball data from Cricsheet (ODC-By 1.0).
    </p>
  );
}

/** Header shortcut to any season page; shows the season being viewed. */
function SeasonJump() {
  const seasons = useSeasonsQuery();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const current = /^\/seasons\/(\d{4})/.exec(pathname)?.[1] ?? '';
  const years = (seasons.data ?? []).map((s) => s.year).sort((a, b) => b - a);
  if (years.length === 0) return null;
  return (
    <label className="flex items-center gap-2 text-sm">
      <CalendarRange aria-hidden="true" className="hidden size-4 text-muted-foreground sm:block" />
      <span className="sr-only">Go to season</span>
      <select
        value={current}
        onChange={(event) => event.target.value && void navigate(`/seasons/${event.target.value}`)}
        className="h-9 rounded-lg border bg-card px-2.5 text-sm font-medium shadow-sm transition-colors hover:border-primary/50 focus-visible:outline-2 focus-visible:outline-ring"
      >
        <option value="">Season…</option>
        {years.map((y) => (
          <option key={y} value={y}>
            IPL {y}
          </option>
        ))}
      </select>
    </label>
  );
}

const THEME_ORDER: ThemePreference[] = ['light', 'dark', 'system'];
const THEME_ICON = { light: Sun, dark: Moon, system: Monitor } as const;

function ThemeToggle() {
  const { preference, setPreference } = useTheme();
  const next = THEME_ORDER[(THEME_ORDER.indexOf(preference) + 1) % THEME_ORDER.length] ?? 'system';
  const Icon = THEME_ICON[preference];
  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={() => setPreference(next)}
      aria-label={`Theme: ${preference}. Switch to ${next}`}
      title={`Theme: ${preference}`}
    >
      <Icon aria-hidden="true" />
    </Button>
  );
}

/** Sidebar on large screens, a slide-over menu on small ones; the page content scrolls independently. */
export function AppLayout() {
  const location = useLocation();
  // The menu belongs to the page it was opened on: navigating closes it without an effect.
  const [menuPath, setMenuPath] = useState<string | null>(null);
  const menuOpen = menuPath === location.pathname;
  const setMenuOpen = (open: boolean) => setMenuPath(open ? location.pathname : null);

  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setMenuPath(null);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [menuOpen]);

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
      <a
        href="#main-content"
        className="sr-only z-50 rounded-md bg-primary px-3 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        Skip to content
      </a>
      <aside className="sticky top-0 hidden h-screen flex-col gap-6 overflow-y-auto bg-sidebar px-3 py-5 lg:flex">
        <Brand />
        <SidebarNav />
        <SidebarFooter />
      </aside>

      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b bg-background/75 px-4 backdrop-blur-md supports-[backdrop-filter]:bg-background/60 sm:px-6">
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden"
            aria-label="Open navigation"
            aria-expanded={menuOpen}
            aria-controls="mobile-navigation"
            onClick={() => setMenuOpen(true)}
          >
            <Menu aria-hidden="true" />
          </Button>
          <Link to="/" className="font-semibold tracking-tight lg:hidden">
            IPLytics
          </Link>
          <p className="hidden text-sm text-muted-foreground lg:block">
            IPL Analytics &amp; Intelligence Platform
          </p>
          <div className="ml-auto flex items-center gap-1 sm:gap-2">
            <SeasonJump />
            <ThemeToggle />
          </div>
        </header>

        {menuOpen && (
          <div
            id="mobile-navigation"
            className="fixed inset-0 z-40 lg:hidden"
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
          >
            <button
              type="button"
              aria-label="Close navigation"
              className="absolute inset-0 bg-black/50 backdrop-blur-sm"
              onClick={() => setMenuOpen(false)}
            />
            <div className="animate-slide-in relative flex h-full w-72 max-w-[85vw] flex-col gap-6 overflow-y-auto bg-sidebar px-3 py-5 shadow-xl">
              <div className="flex items-center justify-between">
                <Brand />
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Close navigation"
                  className="text-sidebar-foreground hover:bg-sidebar-accent"
                  onClick={() => setMenuOpen(false)}
                >
                  <X aria-hidden="true" />
                </Button>
              </div>
              <SidebarNav onNavigate={() => setMenuPath(null)} />
            </div>
          </div>
        )}

        <main
          id="main-content"
          tabIndex={-1}
          className="mx-auto w-full max-w-7xl px-4 py-6 outline-none sm:px-6 lg:px-8"
        >
          <Suspense fallback={<PageSkeleton />}>
            <div key={location.pathname.split('/').slice(0, 3).join('/')} className="animate-fade-up">
              <Outlet />
            </div>
          </Suspense>
        </main>
      </div>
    </div>
  );
}
