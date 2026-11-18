/**
 * Visual identity per franchise code (the codes of backend/src/main/resources/reference/franchises.csv).
 *
 * Colours are the franchises' kit colours. `logoUrl` is optional: IPLytics ships original monogram crests and does
 * not bundle official logos (they are trademarks). To show licensed logos, put the files under public/media/teams/
 * and set e.g. `logoUrl: '/media/teams/MI.svg'`; a missing or broken file falls back to the crest.
 */
export interface TeamBranding {
  /** Main kit colour (crest fill, hero gradient start). */
  primary: string;
  /** Accent colour (crest rim, hero gradient end). */
  secondary: string;
  /** Text colour readable on `primary`. */
  onPrimary: string;
  logoUrl?: string;
}

export const TEAM_BRANDING: Readonly<Record<string, TeamBranding>> = {
  CSK: { primary: '#f9cd05', secondary: '#0b4ea2', onPrimary: '#13213c' },
  DC: { primary: '#17479e', secondary: '#ef1b23', onPrimary: '#ffffff' },
  DEC: { primary: '#1f3a5f', secondary: '#b8b8b8', onPrimary: '#ffffff' },
  GL: { primary: '#e04f16', secondary: '#1c1c1c', onPrimary: '#ffffff' },
  GT: { primary: '#1b2133', secondary: '#b9975b', onPrimary: '#ffffff' },
  KKR: { primary: '#3a225d', secondary: '#d4af37', onPrimary: '#ffffff' },
  KTK: { primary: '#f15a22', secondary: '#7b3f99', onPrimary: '#ffffff' },
  LSG: { primary: '#0057e2', secondary: '#f28b00', onPrimary: '#ffffff' },
  MI: { primary: '#004ba0', secondary: '#d1ab3e', onPrimary: '#ffffff' },
  PBKS: { primary: '#d71920', secondary: '#a7a9ac', onPrimary: '#ffffff' },
  PWI: { primary: '#2f9be3', secondary: '#1b1b1b', onPrimary: '#ffffff' },
  RCB: { primary: '#c8102e', secondary: '#1f1f1f', onPrimary: '#ffffff' },
  RPS: { primary: '#5b2d8e', secondary: '#e2328b', onPrimary: '#ffffff' },
  RR: { primary: '#ea1a85', secondary: '#254aa5', onPrimary: '#ffffff' },
  SRH: { primary: '#f26522', secondary: '#1a1a1a', onPrimary: '#ffffff' },
};

/** Neutral palette for unknown codes and players without a team; picked by a stable hash of the name. */
const FALLBACK_PALETTE: readonly TeamBranding[] = [
  { primary: '#334155', secondary: '#64748b', onPrimary: '#ffffff' },
  { primary: '#1e40af', secondary: '#3b82f6', onPrimary: '#ffffff' },
  { primary: '#047857', secondary: '#10b981', onPrimary: '#ffffff' },
  { primary: '#7c2d12', secondary: '#ea580c', onPrimary: '#ffffff' },
  { primary: '#5b21b6', secondary: '#8b5cf6', onPrimary: '#ffffff' },
  { primary: '#9f1239', secondary: '#f43f5e', onPrimary: '#ffffff' },
];

function hash(text: string): number {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) >>> 0;
  return h;
}

/** Branding for a franchise code, or a stable neutral identity derived from `seed` (e.g. the name). */
export function teamBranding(code: string | undefined, seed = code ?? ''): TeamBranding {
  const known = code ? TEAM_BRANDING[code.toUpperCase()] : undefined;
  return known ?? FALLBACK_PALETTE[hash(seed) % FALLBACK_PALETTE.length]!;
}
