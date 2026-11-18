/**
 * Player photo configuration. Photos are looked up by the Cricsheet player id (`sourceId` on player DTOs), which is
 * stable across database rebuilds, unlike the numeric API id. React components never hold image URLs: they call
 * `playerImageUrl`, and a player without a photo gets an initials avatar.
 *
 * Two sources, in order:
 * 1. `PLAYER_IMAGE_OVERRIDES`: explicit URLs for individual players (images you are licensed to use).
 * 2. `VITE_PLAYER_IMAGE_URL_TEMPLATE` (build-time env), e.g. `https://cdn.example.com/players/{sourceId}.webp`
 *    or `/media/players/{sourceId}.webp` for files under public/.
 */
export const PLAYER_IMAGE_OVERRIDES: Readonly<Record<string, string>> = {};

const TEMPLATE = (import.meta.env.VITE_PLAYER_IMAGE_URL_TEMPLATE as string | undefined)?.trim() || undefined;

export function playerImageUrl(
  sourceId: string | undefined,
  overrides: Readonly<Record<string, string>> = PLAYER_IMAGE_OVERRIDES,
  template: string | undefined = TEMPLATE,
): string | undefined {
  if (!sourceId) return undefined;
  return overrides[sourceId] ?? template?.replaceAll('{sourceId}', encodeURIComponent(sourceId));
}

/** "Rohit Sharma" → "RS", "RG Sharma" → "RS", "Rashid Khan" → "RK". */
export function initials(name: string | undefined): string {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  const first = words[0]!;
  const last = words.length > 1 ? words[words.length - 1]! : '';
  return (first.charAt(0) + last.charAt(0)).toUpperCase();
}
