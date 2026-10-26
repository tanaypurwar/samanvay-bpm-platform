export interface FieldError {
  field: string;
  message: string;
}

/** Error returned by the tasklist backend, normalized for the UI. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: FieldError[] = [],
    readonly correlationId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const TIMEOUT_MS = 20_000;

let serverUrl = '';
let csrf: { headerName: string; token: string } | null = null;
let onUnauthorized: (() => void) | null = null;

/** Base URL of the Samanvay Tasklist backend, e.g. https://tasks.example.com */
export function setServerUrl(url: string) {
  serverUrl = normalizeServerUrl(url);
  csrf = null;
}

export function getServerUrl() {
  return serverUrl;
}

export function normalizeServerUrl(url: string): string {
  const trimmed = url.trim().replace(/\/+$/, '');
  return /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

/** Forget the CSRF token, e.g. after sign-out (the server issues a new one). */
export function resetCsrf() {
  csrf = null;
}

/**
 * Native apps cannot read the XSRF-TOKEN cookie the browser app uses, so the token is fetched from
 * /api/auth/csrf. The session and XSRF cookies themselves are kept by the platform's cookie store.
 */
async function csrfHeader(): Promise<Record<string, string>> {
  if (!csrf) {
    const response = await send('/api/auth/csrf', { method: 'GET', headers: { Accept: 'application/json' } });
    if (!response.ok) {
      throw await toApiError(response);
    }
    csrf = await response.json();
  }
  return { [csrf!.headerName]: csrf!.token };
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** Don't trigger the global "session expired" handler on 401 (login and the start-up session check). */
  silentUnauthorized?: boolean;
}

export async function api<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  if (!serverUrl) {
    throw new ApiError(0, 'NO_SERVER', 'Enter the server address first.');
  }
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    Object.assign(headers, await csrfHeader());
  }

  const response = await send(path, {
    method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  // a stale CSRF token (e.g. after the server restarted) is answered with 403: refresh it once
  if (response.status === 403 && method !== 'GET' && !retried) {
    csrf = null;
    return api<T>(path, options, true);
  }
  if (response.status === 401 && !options.silentUnauthorized) {
    onUnauthorized?.();
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    return await fetch(`${serverUrl}${path}`, { ...init, credentials: 'include', signal: controller.signal });
  } catch {
    throw new ApiError(0, 'NETWORK', `Cannot reach ${serverUrl}. Check the address and your connection.`);
  } finally {
    clearTimeout(timer);
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let data: Partial<{ code: string; message: string; fieldErrors: FieldError[]; correlationId: string }> = {};
  try {
    data = await response.json();
  } catch {
    // non-JSON error body
  }
  return new ApiError(
    response.status,
    data.code ?? `HTTP_${response.status}`,
    data.message ?? 'Something went wrong. Please try again.',
    data.fieldErrors ?? [],
    data.correlationId,
  );
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.correlationId ? `${error.message} (ref ${error.correlationId.substring(0, 8)})` : error.message;
  }
  return 'Something went wrong. Please try again.';
}
