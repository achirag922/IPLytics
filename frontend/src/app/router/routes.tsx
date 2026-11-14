import type { RouteObject } from 'react-router';
import { PageSkeleton } from '@/components/loading/Skeletons';
import { AppLayout } from '../AppLayout/AppLayout';
import { RouteErrorPage } from './RouteErrorPage';

/** Each feature is its own chunk, loaded on first visit (lazy route loading). */
export const routes: RouteObject[] = [
  {
    path: '/',
    Component: AppLayout,
    HydrateFallback: PageSkeleton,
    ErrorBoundary: RouteErrorPage,
    children: [
      {
        index: true,
        lazy: () =>
          import('@/features/dashboard/DashboardPage').then((m) => ({ Component: m.DashboardPage })),
      },

      {
        path: 'teams',
        lazy: () => import('@/features/teams/TeamsPage').then((m) => ({ Component: m.TeamsPage })),
      },
      {
        path: 'teams/:teamId',
        lazy: () => import('@/features/teams/TeamDetailPage').then((m) => ({ Component: m.TeamDetailPage })),
        children: [
          {
            index: true,
            lazy: () => import('@/features/teams/TeamTabs').then((m) => ({ Component: m.TeamOverviewTab })),
          },
          {
            path: 'seasons',
            lazy: () => import('@/features/teams/TeamTabs').then((m) => ({ Component: m.TeamSeasonsTab })),
          },
          {
            path: 'batting',
            lazy: () => import('@/features/teams/TeamTabs').then((m) => ({ Component: m.TeamBattingTab })),
          },
          {
            path: 'bowling',
            lazy: () => import('@/features/teams/TeamTabs').then((m) => ({ Component: m.TeamBowlingTab })),
          },
          {
            path: 'venues',
            lazy: () => import('@/features/teams/TeamTabs').then((m) => ({ Component: m.TeamVenuesTab })),
          },
          {
            path: 'head-to-head',
            lazy: () => import('@/features/teams/TeamTabs').then((m) => ({ Component: m.TeamHeadToHeadTab })),
          },
        ],
      },

      {
        path: 'players',
        lazy: () => import('@/features/players/PlayersPage').then((m) => ({ Component: m.PlayersPage })),
      },
      {
        path: 'players/:playerId',
        lazy: () =>
          import('@/features/players/PlayerDetailPage').then((m) => ({ Component: m.PlayerDetailPage })),
        children: [
          {
            index: true,
            lazy: () =>
              import('@/features/players/PlayerTabs').then((m) => ({ Component: m.PlayerOverviewTab })),
          },
          {
            path: 'batting',
            lazy: () =>
              import('@/features/players/PlayerTabs').then((m) => ({ Component: m.PlayerBattingTab })),
          },
          {
            path: 'bowling',
            lazy: () =>
              import('@/features/players/PlayerTabs').then((m) => ({ Component: m.PlayerBowlingTab })),
          },
          {
            path: 'phases',
            lazy: () =>
              import('@/features/players/PlayerTabs').then((m) => ({ Component: m.PlayerPhasesTab })),
          },
          {
            path: 'vs-teams',
            lazy: () =>
              import('@/features/players/PlayerTabs').then((m) => ({ Component: m.PlayerVsTeamsTab })),
          },
          {
            path: 'venues',
            lazy: () =>
              import('@/features/players/PlayerTabs').then((m) => ({ Component: m.PlayerVenuesTab })),
          },
        ],
      },

      {
        path: 'matches',
        lazy: () => import('@/features/matches/MatchesPage').then((m) => ({ Component: m.MatchesPage })),
      },
      {
        path: 'matches/:matchId',
        lazy: () =>
          import('@/features/matches/MatchDetailPage').then((m) => ({ Component: m.MatchDetailPage })),
        children: [
          {
            index: true,
            lazy: () => import('@/features/matches/MatchTabs').then((m) => ({ Component: m.ScorecardTab })),
          },
          {
            path: 'progression',
            lazy: () => import('@/features/matches/MatchTabs').then((m) => ({ Component: m.ProgressionTab })),
          },
        ],
      },

      {
        path: 'venues',
        lazy: () => import('@/features/venues/VenuesPage').then((m) => ({ Component: m.VenuesPage })),
      },
      {
        path: 'venues/:venueId',
        lazy: () =>
          import('@/features/venues/VenueDetailPage').then((m) => ({ Component: m.VenueDetailPage })),
      },

      {
        path: 'seasons',
        lazy: () => import('@/features/seasons/SeasonsPage').then((m) => ({ Component: m.SeasonsPage })),
      },
      {
        path: 'seasons/:year',
        lazy: () =>
          import('@/features/seasons/SeasonDetailPage').then((m) => ({ Component: m.SeasonDetailPage })),
      },

      {
        path: 'head-to-head',
        lazy: () =>
          import('@/features/headToHead/HeadToHeadPage').then((m) => ({ Component: m.HeadToHeadPage })),
      },
      {
        path: 'compare',
        lazy: () => import('@/features/comparison/ComparePage').then((m) => ({ Component: m.ComparePage })),
      },
      {
        path: 'matchups',
        lazy: () => import('@/features/matchups/MatchupsPage').then((m) => ({ Component: m.MatchupsPage })),
      },
      {
        path: 'leaderboards',
        lazy: () =>
          import('@/features/leaderboards/LeaderboardsPage').then((m) => ({ Component: m.LeaderboardsPage })),
      },

      {
        path: '*',
        lazy: () => import('@/features/notFound/NotFoundPage').then((m) => ({ Component: m.NotFoundPage })),
      },
    ],
  },
];
