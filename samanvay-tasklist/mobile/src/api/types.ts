// Mirrors the Samanvay Tasklist API (see ../../frontend/src/api/types.ts).

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

/** A JSON Schema (draft-07) as stored by the form admin. Only the parts the mobile renderer reads are typed. */
export interface JsonSchema {
  type?: string;
  title?: string;
  description?: string;
  format?: string;
  readOnly?: boolean;
  required?: string[];
  properties?: Record<string, JsonSchema>;
  enum?: unknown[];
  const?: unknown;
  oneOf?: JsonSchema[];
  allOf?: JsonSchema[];
  if?: JsonSchema;
  then?: JsonSchema;
  else?: JsonSchema;
  minLength?: number;
  maxLength?: number;
  minimum?: number;
  maximum?: number;
  default?: unknown;
  [keyword: string]: unknown;
}

export type UiSchema = Record<string, unknown>;

export interface TaskForm {
  formVersionId?: number;
  formKey?: string;
  formName?: string;
  formVersion?: number;
  schema?: JsonSchema;
  uiSchema?: UiSchema;
  formData?: FormData;
  /** Read-only variables, present when the task has no configured form. */
  variables?: Record<string, unknown>;
}

export type Scope = 'mine' | 'group' | 'all';
