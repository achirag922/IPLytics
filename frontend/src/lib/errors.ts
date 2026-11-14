import { ApiError } from '@/api/client';

/** A friendly explanation per failure kind; raw API messages are never shown to users. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.kind) {
      case 'not-found':
        return 'We could not find what you were looking for. It may not exist in the data set.';
      case 'bad-request':
      case 'unprocessable':
        return 'These filters cannot be applied. Try different filters.';
      case 'network':
        return 'The server could not be reached. Check that the backend is running.';
      default:
        return 'Something went wrong on the server. Please try again.';
    }
  }
  return 'Something went wrong. Please try again.';
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.kind === 'not-found';
}
