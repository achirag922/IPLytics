import { useSystemStatus } from './useSystemStatus';

export function SystemStatusCard() {
  const { data, isPending, isError } = useSystemStatus();

  let dotClass = 'bg-slate-400';
  let text = 'Checking backend…';
  if (isError) {
    dotClass = 'bg-red-500';
    text = 'Backend unreachable. Start it with ./mvnw spring-boot:run in backend/.';
  } else if (!isPending) {
    dotClass = 'bg-emerald-500';
    text = `API ${data.version} · database schema v${data.schemaVersion ?? 'none'}`;
  }

  return (
    <section
      aria-label="System status"
      className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm"
    >
      <span aria-hidden="true" className={`h-2.5 w-2.5 rounded-full ${dotClass}`} />
      <span>{text}</span>
    </section>
  );
}
