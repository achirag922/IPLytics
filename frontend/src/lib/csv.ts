/** CSV export of analytics shown in the app (RFC 4180, UTF-8 with BOM so spreadsheets detect the encoding). */

export type CsvValue = string | number | boolean | null | undefined;

export interface CsvColumn<T> {
  header: string;
  value: (row: T) => CsvValue;
}

const DASH = '–';

/**
 * One CSV field. Display artefacts are undone so spreadsheets get numbers ("7,329" -> 7329, "–" -> empty).
 * Text starting with = + - @ is prefixed with ' so a spreadsheet never runs it as a formula.
 */
export function csvField(value: CsvValue): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : '';
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  let text = value.trim();
  if (text === DASH) return '';
  if (/^[+-]?\d{1,3}(,\d{3})+(\.\d+)?%?$/.test(text)) text = text.replace(/,/g, '');
  if (/^[=+\-@\t\r]/.test(text) && !/^[+-]?\d+(\.\d+)?%?$/.test(text)) text = `'${text}`;
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv<T>(columns: CsvColumn<T>[], rows: T[]): string {
  const lines = [columns.map((c) => csvField(c.header)).join(',')];
  for (const row of rows) lines.push(columns.map((c) => csvField(c.value(row))).join(','));
  return lines.join('\r\n') + '\r\n';
}

/** "Batting leaderboard" + filters -> "batting-leaderboard_season-2024.csv". */
export function csvFileName(base: string, filters: Record<string, string | number | undefined> = {}): string {
  const slug = (text: string) =>
    text
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '');
  const parts = Object.entries(filters)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${slug(k)}-${slug(String(v))}`);
  return `${[slug(base), ...parts].join('_')}.csv`;
}

/** Saves the CSV through a temporary object URL (no server round trip). */
export function downloadCsv(fileName: string, csv: string): void {
  const blob = new Blob(['﻿', csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** A cell's text without decorative content (avatars, crests and icons are aria-hidden). */
function cellText(cell: Element): string {
  const copy = cell.cloneNode(true) as Element;
  copy.querySelectorAll('[aria-hidden="true"]').forEach((node) => node.remove());
  return copy.textContent ?? '';
}

/** Reads a rendered table (header titles, cell text) as CSV columns: exports exactly what is displayed. */
export function tableToCsv(table: HTMLTableElement): string {
  const headers = [...table.querySelectorAll('thead th')].map(
    (th) => th.querySelector('abbr')?.getAttribute('title') ?? th.textContent ?? '',
  );
  const rows = [...table.querySelectorAll('tbody tr')].map((tr) =>
    [...tr.querySelectorAll('td')].map(cellText),
  );
  return toCsv(
    headers.map((header, index) => ({ header, value: (row: string[]) => row[index] })),
    rows,
  );
}
