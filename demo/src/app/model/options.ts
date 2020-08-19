export interface Options {
  serverUrl: string;
  fileKey: string;
  requestMethod: 'POST' | 'PUT';
  headers: Array<{id: number, key: string, value: string}>;
  parameters: Object
}