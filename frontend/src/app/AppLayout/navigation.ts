import {
  CalendarDays,
  Crosshair,
  GitCompareArrows,
  LayoutDashboard,
  ListOrdered,
  MapPin,
  Shield,
  Swords,
  Trophy,
  Users,
  type LucideIcon,
} from 'lucide-react';

export interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
}

export interface NavGroup {
  label?: string;
  items: NavItem[];
}

export const NAVIGATION: NavGroup[] = [
  {
    items: [
      { label: 'Dashboard', to: '/', icon: LayoutDashboard },
      { label: 'Teams', to: '/teams', icon: Shield },
      { label: 'Players', to: '/players', icon: Users },
      { label: 'Matches', to: '/matches', icon: CalendarDays },
      { label: 'Venues', to: '/venues', icon: MapPin },
      { label: 'Seasons', to: '/seasons', icon: Trophy },
    ],
  },
  {
    label: 'Analytics',
    items: [
      { label: 'Head to Head', to: '/head-to-head', icon: Swords },
      { label: 'Player Comparison', to: '/compare', icon: GitCompareArrows },
      { label: 'Matchups', to: '/matchups', icon: Crosshair },
      { label: 'Leaderboards', to: '/leaderboards', icon: ListOrdered },
    ],
  },
];
