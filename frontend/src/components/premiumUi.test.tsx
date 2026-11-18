import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MatchCard, PlayerCard, TeamCard } from '@/components/cards/EntityCards';
import { PlayerLink, TeamLink } from '@/components/cards/EntityLinks';
import { RivalryBanner } from '@/components/cards/RivalryBanner';
import { EntityHero } from '@/components/layout/EntityHero';
import { PlayerAvatar } from '@/components/media/PlayerAvatar';
import { TeamLogo } from '@/components/media/TeamLogo';
import { SegmentedControl } from '@/components/ui/segmented';
import { initials, playerImageUrl } from '@/config/media/playerImages';
import { TEAM_BRANDING, teamBranding } from '@/config/media/teamBranding';
import { tableToCsv } from '@/lib/csv';
import { matchSummary, teams } from '@/test/fixtures';
import { renderApp, renderWithProviders } from '@/test/renderApp';

describe('Media configuration', () => {
  it('resolves player photos from overrides first, then the URL template, by Cricsheet id', () => {
    const overrides = { abc123: 'https://img.example/rohit.webp' };
    expect(playerImageUrl('abc123', overrides, '/media/{sourceId}.webp')).toBe(
      'https://img.example/rohit.webp',
    );
    expect(playerImageUrl('x y', {}, '/media/players/{sourceId}.webp')).toBe('/media/players/x%20y.webp');
    expect(playerImageUrl('abc123', {}, undefined)).toBeUndefined();
    expect(playerImageUrl(undefined, overrides, '/m/{sourceId}')).toBeUndefined();
  });

  it('builds initials and stable team colours', () => {
    expect(initials('RG Sharma')).toBe('RS');
    expect(initials('Rashid Khan')).toBe('RK');
    expect(initials('Dhoni')).toBe('D');
    expect(initials(undefined)).toBe('?');
    expect(teamBranding('mi')).toBe(TEAM_BRANDING.MI);
    // Unknown codes get a neutral identity that does not change between calls.
    expect(teamBranding('XYZ', 'Somewhere XI')).toEqual(teamBranding('XYZ', 'Somewhere XI'));
  });

  it('has branding for every franchise code in the reference data', () => {
    for (const code of [
      'CSK',
      'DC',
      'DEC',
      'GL',
      'GT',
      'KKR',
      'KTK',
      'LSG',
      'MI',
      'PBKS',
      'PWI',
      'RCB',
      'RPS',
      'RR',
      'SRH',
    ]) {
      expect(TEAM_BRANDING[code], code).toBeDefined();
    }
  });
});

describe('TeamLogo and PlayerAvatar', () => {
  it('draws a decorative crest with the team code, or a labelled one', () => {
    const { rerender } = renderWithProviders(<TeamLogo team={{ code: 'MI', name: 'Mumbai Indians' }} />);
    const crest = screen.getByTestId('team-crest');
    expect(crest).toHaveAttribute('aria-hidden', 'true');
    expect(crest).toHaveTextContent('MI');
    rerender(<TeamLogo team={{ code: 'MI' }} label="Mumbai Indians crest" />);
    expect(screen.getByRole('img', { name: 'Mumbai Indians crest' })).toBeInTheDocument();
  });

  it('shows initials when a player has no configured photo', () => {
    renderWithProviders(
      <PlayerAvatar player={{ name: 'Virat Kohli', sourceId: 'ba607b88' }} teamCode="RCB" />,
    );
    expect(screen.getByTestId('avatar-fallback')).toHaveTextContent('VK');
    expect(document.querySelector('img')).toBeNull();
  });

  it('adds media to entity links only when asked', () => {
    renderWithProviders(
      <>
        <TeamLink team={{ id: 4, code: 'MI', name: 'Mumbai Indians' }} logo />
        <PlayerLink player={{ id: 1, name: 'RG Sharma' }} avatar teamCode="MI" />
        <PlayerLink player={{ id: 2, name: 'MS Dhoni' }} />
      </>,
    );
    expect(screen.getByRole('link', { name: 'Mumbai Indians' })).toHaveAttribute('href', '/teams/4');
    expect(screen.getByRole('link', { name: 'RG Sharma' })).toBeInTheDocument();
    expect(screen.getAllByTestId('avatar-fallback')).toHaveLength(1);
    expect(screen.getAllByTestId('team-crest')).toHaveLength(1);
  });
});

describe('Entity cards', () => {
  it('shows a match with both scores, the winner and a link to the scorecard', () => {
    renderWithProviders(<MatchCard match={matchSummary(77, { stage: 'Final' })} />);
    const card = screen.getByRole('link', { name: 'Mumbai Indians v Chennai Super Kings, 10 Apr 2019' });
    expect(card).toHaveAttribute('href', '/matches/77');
    expect(within(card).getByText('170/5')).toHaveClass('font-bold');
    expect(within(card).getByText('Final')).toBeInTheDocument();
    expect(within(card).getByText('Mumbai Indians won by 10 runs')).toBeInTheDocument();
  });

  it('shows a team record and a player leader', () => {
    const team = teams.find((t) => t.code === 'MI')!;
    renderWithProviders(
      <>
        <TeamCard team={team} />
        <PlayerCard label="Most runs" player={{ id: 9, name: 'V Kohli' }} value="741" hint="Orange cap" />
      </>,
    );
    expect(screen.getByRole('link', { name: /Mumbai Indians/ })).toHaveAttribute('href', `/teams/${team.id}`);
    expect(screen.getByRole('link', { name: /V Kohli/ })).toHaveTextContent('741');
  });

  it('renders a detail hero with headline figures and a rivalry banner', () => {
    renderWithProviders(
      <>
        <EntityHero title="Mumbai Indians" eyebrow="Franchise" stats={[{ label: 'Titles', value: '5' }]} />
        <RivalryBanner
          a={{ team: { id: 4, code: 'MI', name: 'Mumbai Indians' }, wins: 20 }}
          b={{ team: { id: 2, code: 'CSK', name: 'Chennai Super Kings' }, wins: 15 }}
          matches={36}
        />
      </>,
    );
    expect(screen.getByRole('heading', { level: 1, name: 'Mumbai Indians' })).toBeInTheDocument();
    expect(screen.getByText('Titles').nextSibling).toHaveTextContent('5');
    expect(screen.getByRole('region', { name: 'Rivalry summary' })).toHaveTextContent('20–15');
    expect(screen.getByText('36 meetings')).toBeInTheDocument();
  });
});

describe('SegmentedControl', () => {
  it('marks the chosen option and reports changes', async () => {
    const onChange = vi.fn();
    const { user } = renderWithProviders(
      <SegmentedControl
        label="View"
        value="cards"
        options={[
          { value: 'cards', label: 'Cards' },
          { value: 'table', label: 'Table' },
        ]}
        onChange={onChange}
      />,
    );
    expect(screen.getByRole('button', { name: 'Cards' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'Table' }));
    expect(onChange).toHaveBeenCalledWith('table');
  });
});

describe('Table CSV export with media', () => {
  it('leaves avatars and crests out of the exported text', () => {
    const { container } = renderWithProviders(
      <table>
        <thead>
          <tr>
            <th>Player</th>
            <th>Team</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>
              <PlayerLink player={{ id: 1, name: 'RG Sharma' }} avatar />
            </td>
            <td>
              <TeamLink team={{ id: 4, code: 'MI', name: 'Mumbai Indians' }} short logo />
            </td>
          </tr>
        </tbody>
      </table>,
    );
    expect(tableToCsv(container.querySelector('table')!)).toContain('RG Sharma,MI');
  });
});

describe('V1.7 pages', () => {
  it('lists teams as cards grouped by current and former franchises', async () => {
    renderApp('/teams');
    const current = await screen.findByRole('region', { name: 'Current franchises' });
    expect(within(current).getByRole('link', { name: /Mumbai Indians/ })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Former franchises' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: /IPL franchises/ })).toBeInTheDocument();
  });

  it('switches the matches list between cards and table through the URL', async () => {
    const { user, location } = renderApp('/matches');
    const cards = await screen.findByRole('region', { name: 'Matches matching the filters' });
    expect(
      within(cards).getAllByRole('link', { name: /Mumbai Indians v Chennai Super Kings/ }).length,
    ).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: 'Table' }));
    expect(location()).toBe('/matches?view=table');
    expect(await screen.findByRole('table', { name: 'Matches matching the filters' })).toBeInTheDocument();
  });

  it('shows the team hero with titles from the seasons API', async () => {
    renderApp('/teams/4');
    expect(await screen.findByRole('img', { name: 'Mumbai Indians crest' })).toBeInTheDocument();
    expect(screen.getByText('Titles')).toBeInTheDocument();
  });

  it('jumps to a season from the header', async () => {
    const { user, location } = renderApp('/teams');
    const jump = await screen.findByRole('combobox', { name: 'Go to season' });
    await screen.findByRole('option', { name: 'IPL 2025' });
    await user.selectOptions(jump, '2025');
    await waitFor(() => expect(location()).toBe('/seasons/2025'), { timeout: 8000 });
  });
});
