import { Menu, Monitor, Moon, Sun, X } from 'lucide-react';
import { Suspense, useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { PageSkeleton } from '@/components/loading/Skeletons';
import { Button } from '@/components/ui/button';
import { useTheme, type ThemePreference } from '@/lib/theme';
import { cn } from '@/lib/utils';
import { NAVIGATION } from './navigation';

function Brand() {
  return (
    <Link
      to="/"
      className="flex items-center gap-2 px-3 text-lg font-semibold tracking-tight text-sidebar-accent-foreground"
    >
      <span
        aria-hidden="true"
        className="grid size-7 place-items-center rounded-md bg-primary text-xs font-bold text-primary-foreground"
      >
        IPL
      </span>
      IPLytics
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
                      'flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-sidebar-accent text-sidebar-accent-foreground'
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
      </aside>

      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b bg-background/90 px-4 backdrop-blur sm:px-6">
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
          <div className="ml-auto">
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
              className="absolute inset-0 bg-black/40"
              onClick={() => setMenuOpen(false)}
            />
            <div className="relative flex h-full w-72 max-w-[85vw] flex-col gap-6 overflow-y-auto bg-sidebar px-3 py-5 shadow-xl">
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
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  );
}
