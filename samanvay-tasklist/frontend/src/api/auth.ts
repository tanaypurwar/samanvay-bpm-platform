import { api, ApiError, readCookie } from './client';
import type { User } from './types';

/** Current session user, or null when signed out. Also makes the backend issue the CSRF cookie. */
export async function fetchCurrentUser(): Promise<User | null> {
  try {
    return await api<User>('/api/auth/me', { silentUnauthorized: true });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export async function login(username: string, password: string): Promise<User> {
  if (!readCookie('XSRF-TOKEN')) {
    await fetchCurrentUser();
  }
  return api<User>('/api/auth/login', {
    method: 'POST',
    body: { username, password },
    silentUnauthorized: true,
  });
}

export function logout(): Promise<void> {
  return api<void>('/api/auth/logout', { method: 'POST', silentUnauthorized: true });
}
