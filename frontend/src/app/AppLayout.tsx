import { Link, NavLink, Outlet } from 'react-router';
import { NAV_SECTIONS } from './navigation';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-1.5 text-sm font-medium ${
    isActive ? 'bg-indigo-600 text-white' : 'text-slate-700 hover:bg-slate-100'
  }`;

export function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-4 px-4 py-3">
          <Link to="/" className="text-xl font-bold tracking-tight text-indigo-700">
            IPLytics
          </Link>
          <nav aria-label="Main" className="flex flex-wrap gap-1">
            {NAV_SECTIONS.map((section) => (
              <NavLink key={section.path} to={section.path} className={linkClass}>
                {section.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
