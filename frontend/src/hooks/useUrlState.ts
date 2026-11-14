import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';

export type UrlValue = string | number | readonly (string | number)[] | null | undefined;

export interface UrlState {
  params: URLSearchParams;
  get: (key: string) => string | undefined;
  /** Integer value of a parameter; undefined when missing or not a whole number. */
  getNumber: (key: string) => number | undefined;
  /** Comma-separated integers (e.g. ids=12,40). */
  getNumbers: (key: string) => number[];
  /**
   * Sets or clears parameters (null/undefined/'' clears). Each change is a new history entry, so back/forward
   * walk through filter states. Changing any filter resets pagination unless the update sets the page itself.
   */
  update: (updates: Record<string, UrlValue>) => void;
}

/** Filters live in the URL: refresh, share and back/forward all preserve them. */
export function useUrlState(): UrlState {
  const [params, setParams] = useSearchParams();

  const update = useCallback(
    (updates: Record<string, UrlValue>) => {
      setParams((previous) => {
        const next = new URLSearchParams(previous);
        for (const [key, value] of Object.entries(updates)) {
          const text = Array.isArray(value)
            ? value.join(',')
            : value === null || value === undefined
              ? ''
              : String(value);
          if (text === '') next.delete(key);
          else next.set(key, text);
        }
        if (!('page' in updates)) next.delete('page');
        return next;
      });
    },
    [setParams],
  );

  return useMemo(() => {
    const get = (key: string) => params.get(key) ?? undefined;
    const getNumber = (key: string) => {
      const value = params.get(key);
      if (value === null || value.trim() === '') return undefined;
      const number = Number(value);
      return Number.isInteger(number) ? number : undefined;
    };
    const getNumbers = (key: string) =>
      (params.get(key) ?? '')
        .split(',')
        .map((part) => Number(part))
        .filter((n) => Number.isInteger(n) && n > 0);
    return { params, get, getNumber, getNumbers, update };
  }, [params, update]);
}
