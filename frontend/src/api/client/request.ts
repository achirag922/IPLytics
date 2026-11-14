import { apiGet, type QueryParams } from './httpClient';
import type { Envelope, Page } from './types';

/** GET a single resource and unwrap {data}. */
export async function getData<T>(path: string, params?: QueryParams, signal?: AbortSignal): Promise<T> {
  const body = await apiGet<Envelope<T>>(path, params, signal);
  return body.data;
}

/** GET a collection and unwrap {data, pagination}. */
export async function getPage<T>(path: string, params?: QueryParams, signal?: AbortSignal): Promise<Page<T>> {
  const body = await apiGet<Envelope<T[]>>(path, params, signal);
  return {
    items: body.data,
    pagination: body.pagination ?? {
      page: 0,
      size: body.data.length,
      totalElements: body.data.length,
      totalPages: 1,
    },
  };
}

/** GET an unpaginated collection (returned by the backend as one full page). */
export async function getList<T>(path: string, params?: QueryParams, signal?: AbortSignal): Promise<T[]> {
  return (await getPage<T>(path, params, signal)).items;
}
