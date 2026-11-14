import { Link } from 'react-router';
import { ErrorState } from '@/components/states/ErrorState';
import { Button } from '@/components/ui/button';

/** Last-resort boundary for unexpected render errors (e.g. a chunk that failed to load). */
export function RouteErrorPage() {
  return (
    <main className="mx-auto max-w-xl px-4 py-16">
      <ErrorState onRetry={() => window.location.reload()} />
      <div className="mt-4 text-center">
        <Button asChild variant="link">
          <Link to="/">Back to the dashboard</Link>
        </Button>
      </div>
    </main>
  );
}
