import { api, apiBlob } from './client';
import type { ProcessDocument, TaskDocuments } from './types';

const base = (taskId: string) => `/api/tasks/${encodeURIComponent(taskId)}/documents`;

export const fetchTaskDocuments = (taskId: string) => api<TaskDocuments>(base(taskId));

export function uploadTaskDocument(taskId: string, file: File, documentType: string) {
  const body = new FormData();
  body.append('file', file);
  body.append('documentType', documentType);
  return api<ProcessDocument>(base(taskId), { method: 'POST', multipart: body });
}

export const deleteTaskDocument = (taskId: string, documentId: string) =>
  api<void>(`${base(taskId)}/${encodeURIComponent(documentId)}`, { method: 'DELETE' });

export const fetchDocumentContent = (taskId: string, documentId: string) =>
  apiBlob(`${base(taskId)}/${encodeURIComponent(documentId)}/content`);
