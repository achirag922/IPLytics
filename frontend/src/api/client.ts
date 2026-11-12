/** RFC 9457 problem detail, as returned by the backend for errors. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

export const API_BASE = '/api/v1';

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { Accept: 'application/json' },
    signal,
  });

  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Non-JSON error body (e.g. proxy error page): fall back to the status text.
    }
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.title ?? (response.statusText || `HTTP ${response.status}`),
      problem,
    );
  }

  return (await response.json()) as T;
}
