/**
 * The one HTTP client of the app: every request to /api/v1 goes through apiGet. Pages never call fetch directly
 * (page -> query hook -> API module -> apiGet).
 */

/** RFC 9457 problem detail returned by the backend for errors. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errorId?: string;
  errors?: { parameter: string; message: string }[];
  allowedValues?: string[];
}

export type ApiErrorKind = 'bad-request' | 'not-found' | 'unprocessable' | 'server' | 'network';

export class ApiError extends Error {
  readonly status: number;
  readonly kind: ApiErrorKind;
  readonly problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.kind =
      status === 0
        ? 'network'
        : status === 404
          ? 'not-found'
          : status === 422
            ? 'unprocessable'
            : status >= 400 && status < 500
              ? 'bad-request'
              : 'server';
  }
}

export const API_PATH = '/api/v1';

let apiOrigin: string | undefined;

/** Overrides the origin requests go to (contract tests point it at a running backend). */
export function configureApiOrigin(origin: string | undefined): void {
  apiOrigin = origin;
}

type QueryValue = string | number | boolean | readonly (string | number)[] | null | undefined;
export type QueryParams = Record<string, QueryValue>;

/** Builds the request URL; empty values are left out so the backend applies its defaults. */
export function buildUrl(path: string, params?: QueryParams): string {
  const origin = apiOrigin ?? (typeof window === 'undefined' ? 'http://localhost' : window.location.origin);
  const url = new URL(`${API_PATH}${path}`, origin);
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value === undefined || value === null || value === '') continue;
    url.searchParams.set(key, Array.isArray(value) ? value.join(',') : String(value));
  }
  return url.toString();
}

export async function apiGet<T>(path: string, params?: QueryParams, signal?: AbortSignal): Promise<T> {
  let response: Response;
  try {
    response = await fetch(buildUrl(path, params), { headers: { Accept: 'application/json' }, signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(0, 'The server could not be reached');
  }
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Non-JSON error body (for example a proxy error page).
    }
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.title ?? `HTTP ${response.status}`,
      problem,
    );
  }
  return (await response.json()) as T;
}
