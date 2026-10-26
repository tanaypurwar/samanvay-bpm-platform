import { api, ApiError, resetCsrf } from './client';
import type { FormData, Scope, Task, TaskForm, TaskPage, User } from './types';

export const PAGE_SIZE = 20;

/** Current session user, or null when signed out. */
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

export const login = (username: string, password: string) =>
  api<User>('/api/auth/login', { method: 'POST', body: { username, password }, silentUnauthorized: true });

export async function logout(): Promise<void> {
  try {
    await api<void>('/api/auth/logout', { method: 'POST', silentUnauthorized: true });
  } finally {
    resetCsrf();
  }
}

export function fetchTasks(scope: Scope, q: string, page: number): Promise<TaskPage> {
  const params = new URLSearchParams({ scope, page: String(page), size: String(PAGE_SIZE) });
  if (q.trim()) {
    params.set('q', q.trim());
  }
  return api<TaskPage>(`/api/tasks?${params.toString()}`);
}

const taskPath = (id: string) => `/api/tasks/${encodeURIComponent(id)}`;

export const fetchTask = (id: string) => api<Task>(taskPath(id));

export const fetchTaskForm = (id: string) => api<TaskForm>(`${taskPath(id)}/form`);

export const claimTask = (id: string) => api<Task>(`${taskPath(id)}/claim`, { method: 'POST' });

export const unclaimTask = (id: string) => api<Task>(`${taskPath(id)}/unclaim`, { method: 'POST' });

export const completeTask = (id: string, formVersionId: number | undefined, formData: FormData) =>
  api<void>(`${taskPath(id)}/complete`, { method: 'POST', body: { formVersionId, formData } });
