import { NavLink, useLocation } from 'react-router';
import { cn } from '@/lib/utils';

export interface TabLink {
  label: string;
  /** Path relative to the detail page (e.g. "batting"); "" is the overview. */
  to: string;
}

/**
 * Tabs that are routes: each tab has its own URL, so tabs survive refresh and back/forward. The current filters
 * (search parameters) travel with the user from tab to tab.
 */
export function TabNav({ tabs, label }: { tabs: TabLink[]; label: string }) {
  const { search } = useLocation();
  return (
    <nav aria-label={label} className="mb-6 overflow-x-auto border-b">
      <ul className="flex min-w-max gap-1">
        {tabs.map((tab) => (
          <li key={tab.to}>
            <NavLink
              to={{ pathname: tab.to, search }}
              end={tab.to === ''}
              relative="route"
              className={({ isActive }) =>
                cn(
                  '-mb-px inline-flex h-10 items-center border-b-2 px-3 text-sm font-medium transition-colors',
                  isActive
                    ? 'border-primary text-foreground'
                    : 'border-transparent text-muted-foreground hover:text-foreground',
                )
              }
            >
              {tab.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
