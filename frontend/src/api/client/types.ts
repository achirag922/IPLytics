import type { components, paths } from '../generated/schema';

/** DTO schemas generated from the backend OpenAPI document (npm run generate:api). */
export type Schemas = components['schemas'];

/** Query parameters of a GET operation, as declared in the OpenAPI document. */
export type QueryOf<P extends keyof paths> = paths[P] extends { get: { parameters: { query?: infer Q } } }
  ? NonNullable<Q>
  : never;

export type Pagination = Schemas['Pagination'];

/** The {data, meta} envelope, unwrapped by the API modules. */
export interface Envelope<T> {
  data: T;
  pagination?: Pagination;
  meta?: { [key: string]: unknown };
}

/** A page of a paginated collection. */
export interface Page<T> {
  items: T[];
  pagination: Pagination;
}
