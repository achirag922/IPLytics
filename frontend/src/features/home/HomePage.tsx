import { Link } from 'react-router';
import { NAV_SECTIONS } from '../../app/navigation';
import { SystemStatusCard } from '../system/SystemStatusCard';

export function HomePage() {
  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">IPLytics</h1>
        <p className="text-slate-600">IPL Analytics &amp; Intelligence Platform</p>
      </div>

      <SystemStatusCard />

      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {NAV_SECTIONS.map((section) => (
          <li key={section.path}>
            <Link
              to={section.path}
              className="block h-full rounded-lg border border-slate-200 bg-white p-4 hover:border-indigo-300"
            >
              <div className="flex items-center justify-between">
                <h2 className="font-semibold">{section.label}</h2>
                <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                  {section.plannedFor}
                </span>
              </div>
              <p className="mt-2 text-sm text-slate-600">{section.description}</p>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
