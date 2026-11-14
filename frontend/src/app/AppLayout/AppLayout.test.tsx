import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderApp } from '@/test/renderApp';

describe('Application layout', () => {
  it('has a main navigation with every section', async () => {
    renderApp('/teams');
    const nav = (await screen.findAllByRole('navigation', { name: 'Main' }))[0] as HTMLElement;
    for (const name of [
      'Dashboard',
      'Teams',
      'Players',
      'Matches',
      'Venues',
      'Seasons',
      'Head to Head',
      'Player Comparison',
      'Matchups',
      'Leaderboards',
    ]) {
      expect(within(nav).getByRole('link', { name })).toBeInTheDocument();
    }
    expect(within(nav).getByRole('link', { name: 'Teams' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main-content');
  });

  it('opens and closes the mobile navigation', async () => {
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: 'Open navigation' }));
    const dialog = screen.getByRole('dialog', { name: 'Navigation' });
    await user.click(within(dialog).getByRole('link', { name: 'Players' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { level: 1, name: 'Players' })).toBeInTheDocument();
  });

  it('switches theme', async () => {
    const { user } = renderApp('/');
    await user.click(await screen.findByRole('button', { name: /Theme: system/ }));
    expect(document.documentElement).not.toHaveClass('dark');
    await user.click(screen.getByRole('button', { name: /Theme: light/ }));
    expect(document.documentElement).toHaveClass('dark');
    await user.click(screen.getByRole('button', { name: /Theme: dark/ }));
  });

  it('shows a 404 page for unknown routes', async () => {
    renderApp('/no-such-page');
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to the dashboard' })).toHaveAttribute('href', '/');
  });

  it('treats a malformed id as not found', async () => {
    renderApp('/players/abc');
    expect(await screen.findByRole('heading', { name: 'Player not found' })).toBeInTheDocument();
  });
});
