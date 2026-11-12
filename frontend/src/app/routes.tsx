import type { RouteObject } from 'react-router';
import { ComingSoon } from '../components/ComingSoon';
import { NotFound } from '../components/NotFound';
import { HomePage } from '../features/home/HomePage';
import { AppLayout } from './AppLayout';
import { NAV_SECTIONS } from './navigation';

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      ...NAV_SECTIONS.map((section) => ({
        path: section.path.slice(1),
        element: <ComingSoon section={section} />,
      })),
      { path: '*', element: <NotFound /> },
    ],
  },
];
