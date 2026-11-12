// API response types. From V1 these will be generated from the backend OpenAPI document.

export interface SystemStatus {
  application: string;
  version: string;
  schemaVersion: string | null;
}
