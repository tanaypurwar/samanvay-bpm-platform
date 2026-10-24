import { api } from './client';
import type { FormData, Task, TaskForm, TaskPage, TaskQuery } from './types';

export function fetchTasks(query: TaskQuery): Promise<TaskPage> {
  const params = new URLSearchParams({
    scope: query.scope,
    sort: query.sort,
    order: query.order,
    page: String(query.page),
    size: String(query.size),
  });
  if (query.q.trim()) {
    params.set('q', query.q.trim());
  }
  return api<TaskPage>(`/api/tasks?${params}`);
}

export const fetchTask = (id: string) => api<Task>(`/api/tasks/${encodeURIComponent(id)}`);

export const fetchTaskForm = (id: string) => api<TaskForm>(`/api/tasks/${encodeURIComponent(id)}/form`);

export const claimTask = (id: string) => api<Task>(`/api/tasks/${encodeURIComponent(id)}/claim`, { method: 'POST' });

export const unclaimTask = (id: string) =>
  api<Task>(`/api/tasks/${encodeURIComponent(id)}/unclaim`, { method: 'POST' });

export const completeTask = (id: string, formVersionId: number | undefined, formData: FormData) =>
  api<void>(`/api/tasks/${encodeURIComponent(id)}/complete`, {
    method: 'POST',
    body: { formVersionId, formData },
  });
