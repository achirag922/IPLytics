import { useParams } from 'react-router';

/** A positive integer route parameter (/players/:playerId); undefined when missing or malformed. */
export function useNumericParam(name: string): number | undefined {
  const value = useParams()[name];
  if (value === undefined || !/^\d+$/.test(value)) return undefined;
  const number = Number(value);
  return number > 0 ? number : undefined;
}
