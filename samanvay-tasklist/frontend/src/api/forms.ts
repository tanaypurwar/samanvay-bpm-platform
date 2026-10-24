import type { RJSFSchema, UiSchema } from '@rjsf/utils';
import { api } from './client';
import type { Binding, FormDetail, FormSummary } from './types';

const base = '/api/admin/forms';

export const fetchForms = () => api<FormSummary[]>(base);

export const fetchForm = (id: number) => api<FormDetail>(`${base}/${id}`);

export const createForm = (body: {
  formKey: string;
  name: string;
  description?: string;
  schema?: RJSFSchema;
  uiSchema?: UiSchema;
}) => api<FormDetail>(base, { method: 'POST', body });

export const saveDraft = (
  id: number,
  body: { name: string; description?: string; schema?: RJSFSchema; uiSchema?: UiSchema },
) => api<FormDetail>(`${base}/${id}`, { method: 'PUT', body });

export const publishForm = (id: number) => api<FormDetail>(`${base}/${id}/publish`, { method: 'POST' });

export const addBinding = (
  id: number,
  body: { taskDefinitionKey: string; processDefinitionKey?: string; priority: number },
) => api<Binding>(`${base}/${id}/bindings`, { method: 'POST', body });

export const removeBinding = (id: number, bindingId: number) =>
  api<void>(`${base}/${id}/bindings/${bindingId}`, { method: 'DELETE' });
