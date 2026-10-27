import { api } from './client';
import type { FormData, StartableProcess, StartForm } from './types';

export const fetchStartableProcesses = () => api<StartableProcess[]>('/api/processes');

export const fetchStartForm = (key: string) =>
  api<StartForm>(`/api/processes/${encodeURIComponent(key)}/start-form`);

export interface Attachment {
  file: File;
  documentType: string;
}

/** Starts the process; with attachments, the form data and files go together in one multipart request. */
export function startProcess(
  key: string,
  formVersionId: number | undefined,
  formData: FormData,
  attachments: Attachment[] = [],
) {
  const path = `/api/processes/${encodeURIComponent(key)}/start`;
  if (attachments.length === 0) {
    return api<{ processInstanceId: string }>(path, { method: 'POST', body: { formVersionId, formData } });
  }
  const body = new globalThis.FormData();
  body.append('request', new Blob([JSON.stringify({ formVersionId, formData })], { type: 'application/json' }));
  for (const attachment of attachments) {
    body.append('files', attachment.file);
    body.append('documentTypes', attachment.documentType);
  }
  return api<{ processInstanceId: string }>(path, { method: 'POST', multipart: body });
}
