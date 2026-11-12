import { useQuery } from '@tanstack/react-query';
import { apiGet } from '../../api/client';
import type { SystemStatus } from '../../api/types';

export function useSystemStatus() {
  return useQuery({
    queryKey: ['system', 'status'],
    queryFn: ({ signal }) => apiGet<SystemStatus>('/system/status', signal),
    staleTime: 30_000,
    retry: false,
  });
}
