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

export interface Branding {
  name: string;
  product: string;
  primaryColor: string;
  secondaryColor: string;
  /** Same-site path or https URL of the deployment's logo; absent when it has none. */
  logoUrl?: string | null;
}

export interface StartableProcess {
  key: string;
  name: string;
  description?: string;
  version?: number;
}

export interface StartForm {
  processKey: string;
  processName: string;
  formVersionId?: number;
  formKey?: string;
  formName?: string;
  formVersion?: number;
  /** Absent when the process has no start form. */
  schema?: RJSFSchema;
  uiSchema?: UiSchema;
  documents?: DocumentOptions;
}

export interface DocumentType {
  id: string;
  label: string;
}

/** What may be attached to a process; `enabled` is false when the server has no document store. */
export interface DocumentOptions {
  enabled: boolean;
  types: DocumentType[];
  maxFileSize: number;
  maxFiles: number;
  contentTypes: string[];
}

export interface ProcessDocument {
  id: string;
  type?: string;
  typeLabel?: string;
  fileName?: string;
  contentType: string;
  size: number;
  /** Empty for someone without an account, e.g. a new employee. */
  uploadedBy?: string;
  uploadedByName?: string;
  /** ISO-8601 */
  uploadedAt: string;
  step?: string;
  deletable: boolean;
}

export interface TaskDocuments {
  options: DocumentOptions;
  canUpload: boolean;
  documents: ProcessDocument[];
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
