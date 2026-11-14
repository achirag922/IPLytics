import { Compass } from 'lucide-react';
import { Link } from 'react-router';
import { Button } from '@/components/ui/button';

interface NotFoundPageProps {
  /** What was not found, e.g. "Player". Defaults to a page. */
  resource?: string;
}

/** Unknown routes, and detail pages whose id does not exist. */
export function NotFoundPage({ resource }: NotFoundPageProps) {
  return (
    <section className="mx-auto flex max-w-lg flex-col items-center gap-3 py-16 text-center">
      <Compass aria-hidden="true" className="size-10 text-muted-foreground" />
      <p className="text-sm font-semibold text-primary">404</p>
      <h1 className="text-2xl font-semibold tracking-tight">
        {resource ? `${resource} not found` : 'Page not found'}
      </h1>
      <p className="text-muted-foreground">
        {resource
          ? `This ${resource.toLowerCase()} is not in the IPLytics data set.`
          : 'The page you are looking for does not exist or has moved.'}
      </p>
      <Button asChild className="mt-2">
        <Link to="/">Go to the dashboard</Link>
      </Button>
    </section>
  );
}
