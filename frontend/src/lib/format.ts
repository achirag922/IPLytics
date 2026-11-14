/** Display formatting only: values arrive computed from the API and are never recalculated here. */

const DASH = '–';
const integer = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const decimal2 = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const decimal3 = new Intl.NumberFormat('en-US', { minimumFractionDigits: 3, maximumFractionDigits: 3 });
const longDate = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
});

export function formatInteger(value: number | null | undefined): string {
  return value === null || value === undefined ? DASH : integer.format(value);
}

export function formatDecimal(value: number | null | undefined): string {
  return value === null || value === undefined ? DASH : decimal2.format(value);
}

/** Net run rate: 3 places with an explicit sign. */
export function formatNetRunRate(value: number | null | undefined): string {
  if (value === null || value === undefined) return DASH;
  return (value > 0 ? '+' : '') + decimal3.format(value);
}

export function formatPercent(value: number | null | undefined): string {
  return value === null || value === undefined ? DASH : `${decimal2.format(value)}%`;
}

/** ISO date (yyyy-MM-dd) to "10 Apr 2019". */
export function formatDate(value: string | null | undefined): string {
  if (!value) return DASH;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? value : longDate.format(date);
}

export function formatText(value: string | null | undefined): string {
  return value === null || value === undefined || value === '' ? DASH : value;
}

/** "13" or "13*" for a not-out score. */
export function formatScore(runs: number, notOut: boolean): string {
  return `${runs}${notOut ? '*' : ''}`;
}

export { DASH };
