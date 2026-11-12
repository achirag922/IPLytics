import type { NavSection } from '../app/navigation';

export function ComingSoon({ section }: { section: NavSection }) {
  return (
    <div className="space-y-2">
      <h1 className="text-2xl font-bold">{section.label}</h1>
      <p className="text-slate-600">{section.description}</p>
      <p className="text-sm text-slate-500">Planned for {section.plannedFor}.</p>
    </div>
  );
}
