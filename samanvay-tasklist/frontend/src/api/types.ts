import type { RJSFSchema, UiSchema } from '@rjsf/utils';

export interface User {
  id: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  groups: string[];
  admin: boolean;
}

/** Engine dates arrive as "2026-10-23T10:15:30.000+0000". */
export interface Task {
  id: string;
  name?: string;
  description?: string;
  assignee?: string;
  owner?: string;
  created?: string;
  due?: string;
  followUp?: string;
  priority?: number;
  processDefinitionId?: string;
  processInstanceId?: string;
  taskDefinitionKey?: string;
}

export interface TaskPage {
  items: Task[];
  total: number;
  page: number;
  size: number;
}

export type FormData = Record<string, unknown>;

export interface TaskForm {
  formVersionId?: number;
  formKey?: string;
  formName?: string;
  formVersion?: number;
  schema?: RJSFSchema;
  uiSchema?: UiSchema;
  formData?: FormData;
  /** Read-only variables, present when the task has no configured form. */
  variables?: Record<string, unknown>;
}

export type Scope = 'mine' | 'group' | 'all';
export type SortField = 'created' | 'dueDate' | 'followUpDate' | 'priority' | 'name';

export interface TaskQuery {
  scope: Scope;
  q: string;
  sort: SortField;
  order: 'asc' | 'desc';
  page: number;
  size: number;
}

export interface FormSummary {
  id: number;
  formKey: string;
  name: string;
  description?: string;
  status: 'DRAFT' | 'PUBLISHED';
  latestVersion?: number;
  updatedAt: string;
}

export interface VersionSummary {
  id: number;
  version: number;
  publishedAt: string;
  publishedBy: string;
}

export interface Binding {
  id: number;
  taskDefinitionKey: string;
  processDefinitionKey?: string;
  priority: number;
}

export interface FormDetail {
  id: number;
  formKey: string;
  name: string;
  description?: string;
  status: 'DRAFT' | 'PUBLISHED';
  draftSchema?: RJSFSchema;
  draftUiSchema?: UiSchema;
  versions: VersionSummary[];
  bindings: Binding[];
}
