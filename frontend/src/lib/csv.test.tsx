import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DataTable } from '@/components/tables/DataTable';
import { renderApp, renderWithProviders } from '@/test/renderApp';
import { requestsTo } from '@/test/server';
import { csvField, csvFileName, toCsv } from './csv';

describe('csv', () => {
  it('escapes fields and undoes display formatting', () => {
    expect(csvField('7,329')).toBe('7329');
    expect(csvField('56.07%')).toBe('56.07%');
    expect(csvField('–')).toBe('');
    expect(csvField('c MS Dhoni b DL Chahar')).toBe('c MS Dhoni b DL Chahar');
    expect(csvField('Delhi, India')).toBe('"Delhi, India"');
    expect(csvField('say "hi"')).toBe('"say ""hi"""');
    expect(csvField('-0.412')).toBe('-0.412');
    expect(csvField('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`);
    expect(csvField(null)).toBe('');
    expect(csvField(12.5)).toBe('12.5');
  });

  it('builds a document and a file name', () => {
    expect(toCsv([{ header: 'Player', value: (r: { n: string }) => r.n }], [{ n: 'A' }, { n: 'B' }])).toBe(
      'Player\r\nA\r\nB\r\n',
    );
    expect(csvFileName('Batting leaderboard', { season: 2024, team: undefined })).toBe(
      'batting-leaderboard_season-2024.csv',
    );
  });
});

/** jsdom's Blob has no text(); FileReader decodes it (and drops the UTF-8 byte-order mark). */
function readBlob(blob: Blob): Promise<string> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).replace(/^/, ''));
    reader.readAsText(blob);
  });
}

/** Captures what downloadCsv saves. */
function captureDownloads() {
  const files: { name: string; text: Promise<string> }[] = [];
  vi.stubGlobal(
    'URL',
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:test'), revokeObjectURL: vi.fn() }),
  );
  const original = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function click(this: HTMLAnchorElement) {
    const blob = (URL.createObjectURL as unknown as { mock: { calls: [Blob][] } }).mock.calls.at(-1)?.[0];
    files.push({ name: this.download, text: blob ? readBlob(blob) : Promise.resolve('') });
  };
  return { files, restore: () => (HTMLAnchorElement.prototype.click = original) };
}

describe('CSV export', () => {
  let downloads: ReturnType<typeof captureDownloads>;
  beforeEach(() => {
    downloads = captureDownloads();
  });
  afterEach(() => downloads.restore());

  it('exports a table exactly as displayed', async () => {
    const { user } = renderWithProviders(
      <DataTable
        caption="Points table"
        columns={[
          { key: 't', header: 'Team', cell: (r: { team: string; nrr: string; runs: string }) => r.team },
          { key: 'n', header: 'NRR', title: 'Net run rate', cell: (r) => r.nrr },
          { key: 'r', header: 'Runs', cell: (r) => r.runs },
        ]}
        rows={[{ team: 'Mumbai Indians', nrr: '+0.412', runs: '2,500' }]}
        rowKey={(r) => r.team}
      />,
    );
    await user.click(screen.getByRole('button', { name: 'Export CSV' }));
    expect(downloads.files[0]?.name).toBe('points-table.csv');
    expect(await downloads.files[0]?.text).toBe('Team,Net run rate,Runs\r\nMumbai Indians,+0.412,2500\r\n');
  });

  it('exports every filtered player by fetching all pages', async () => {
    const { user } = renderApp('/players?team=MI');
    await user.click(await screen.findByRole('button', { name: 'Export all 60' }));
    await waitFor(() => expect(downloads.files).toHaveLength(1));
    const pages = requestsTo('/players').filter((u) => u.searchParams.get('size') === '100');
    expect(pages).toHaveLength(1);
    expect(pages[0]?.searchParams.get('team')).toBe('4');
    expect(downloads.files[0]?.name).toBe('players_team-mi.csv');
    const text = await downloads.files[0]?.text;
    expect(text?.split('\r\n')[0]).toBe(
      'Player,Player id,Latest team,First season,Last season,Matches,Batting innings,Runs,Batting average,Strike rate,Bowling innings,Wickets,Economy',
    );
    expect(text).toContain('RG Sharma,346,MI,2008,2026,270,265,7329,29.5,132.1,30,15,8.1');
  });
});
