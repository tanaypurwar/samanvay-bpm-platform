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

type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler | null = null;

/** Called when an API call reports the session is gone (expired or signed out elsewhere). */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  onUnauthorized = handler;
}

export function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.substring(name.length + 1)) : null;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** Don't trigger the global "session expired" handler on 401 (used by login and the initial session check). */
  silentUnauthorized?: boolean;
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    const token = readCookie('XSRF-TOKEN');
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Check your connection and try again.');
  }

  if (response.status === 401 && !options.silentUnauthorized) {
    onUnauthorized?.();
  }

  if (!response.ok) {
    let data: Partial<{ code: string; message: string; fieldErrors: FieldError[]; correlationId: string }> = {};
    try {
      data = await response.json();
    } catch {
      // non-JSON error body
    }
    throw new ApiError(
      response.status,
      data.code ?? `HTTP_${response.status}`,
      data.message ?? 'Something went wrong. Please try again.',
      data.fieldErrors ?? [],
      data.correlationId,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.correlationId ? `${error.message} (ref ${error.correlationId.substring(0, 8)})` : error.message;
  }
  return 'Something went wrong. Please try again.';
}
