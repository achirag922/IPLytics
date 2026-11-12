import { Link } from 'react-router';

export function NotFound() {
  return (
    <div className="space-y-2">
      <h1 className="text-2xl font-bold">Page not found</h1>
      <Link to="/" className="text-indigo-700 underline">
        Back to home
      </Link>
    </div>
  );
}
