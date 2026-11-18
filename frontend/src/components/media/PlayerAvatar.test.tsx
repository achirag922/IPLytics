import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { renderWithProviders } from '@/test/renderApp';
import { PlayerAvatar } from './PlayerAvatar';
import type * as PlayerImages from '@/config/media/playerImages';

vi.mock('@/config/media/playerImages', async (importOriginal) => {
  const actual = await importOriginal<typeof PlayerImages>();
  return { ...actual, playerImageUrl: (id?: string) => (id ? `/media/players/${id}.webp` : undefined) };
});

describe('PlayerAvatar with a configured photo', () => {
  it('lazy-loads the photo and falls back to initials when it fails', () => {
    renderWithProviders(<PlayerAvatar player={{ name: 'Rohit Sharma', sourceId: '740742ef' }} size="lg" />);
    const img = document.querySelector('img') as HTMLImageElement;
    expect(img).toHaveAttribute('src', '/media/players/740742ef.webp');
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(img).toHaveAttribute('width', '56');
    fireEvent.error(img);
    expect(document.querySelector('img')).toBeNull();
    expect(screen.getByTestId('avatar-fallback')).toHaveTextContent('RS');
  });
});
