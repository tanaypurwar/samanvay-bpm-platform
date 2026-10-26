import type { FormData, JsonSchema, UiSchema } from '../api/types';

/**
 * Turns a form's JSON Schema + RJSF UI schema into a flat list of fields for the native renderer.
 *
 * The web app renders the same schemas with RJSF. RJSF does not run on React Native, so this covers the
 * subset forms use for task data: flat objects of string / number / integer / boolean properties, enums and
 * oneOf/const choices, date and date-time formats, readOnly, required, length and range limits, and
 * conditional rules via if/then/else and allOf. The server re-validates every submission against the full
 * schema, so anything not checked here is still enforced.
 */

export type FieldKind = 'text' | 'textarea' | 'number' | 'integer' | 'boolean' | 'date' | 'datetime' | 'choice';

export interface Choice {
  value: string | number | boolean;
  label: string;
}

export interface FieldModel {
  name: string;
  label: string;
  kind: FieldKind;
  required: boolean;
  readOnly: boolean;
  help?: string;
  choices?: Choice[];
  rows?: number;
  minLength?: number;
  maxLength?: number;
  minimum?: number;
  maximum?: number;
}

/** Applies the if/then/else branches that match the current data, returning required names and constraints. */
export function resolveConditions(schema: JsonSchema, data: FormData): { required: Set<string>; properties: Record<string, JsonSchema> } {
  const required = new Set(schema.required ?? []);
  const properties: Record<string, JsonSchema> = {};
  for (const [name, property] of Object.entries(schema.properties ?? {})) {
    properties[name] = { ...property };
  }

  const apply = (branch: JsonSchema | undefined) => {
    if (!branch) {
      return;
    }
    (branch.required ?? []).forEach((name) => required.add(name));
    for (const [name, extra] of Object.entries(branch.properties ?? {})) {
      properties[name] = { ...(properties[name] ?? {}), ...extra };
    }
    applyConditional(branch);
    (branch.allOf ?? []).forEach(applyConditional);
  };
  const applyConditional = (node: JsonSchema) => {
    if (node.if) {
      apply(matches(node.if, data) ? node.then : node.else);
    }
  };

  applyConditional(schema);
  (schema.allOf ?? []).forEach((sub) => {
    applyConditional(sub);
    if (!sub.if) {
      apply(sub);
    }
  });
  return { required, properties };
}

/** Evaluates an `if` schema against flat form data (const, enum and required are supported). */
export function matches(condition: JsonSchema, data: FormData): boolean {
  for (const name of condition.required ?? []) {
    if (isEmpty(data[name])) {
      return false;
    }
  }
  for (const [name, rule] of Object.entries(condition.properties ?? {})) {
    const value = data[name];
    if (value === undefined) {
      continue; // absent properties satisfy "properties" in JSON Schema
    }
    if ('const' in rule && value !== rule.const) {
      return false;
    }
    if (Array.isArray(rule.enum) && !rule.enum.includes(value)) {
      return false;
    }
  }
  return true;
}

export function buildFields(schema: JsonSchema, uiSchema: UiSchema | undefined, data: FormData): FieldModel[] {
  const { required, properties } = resolveConditions(schema, data);
  const ui = uiSchema ?? {};
  return orderNames(Object.keys(properties), ui['ui:order'] as string[] | undefined)
    .filter((name) => (ui[name] as Record<string, unknown> | undefined)?.['ui:widget'] !== 'hidden')
    .map((name) => toField(name, properties[name], (ui[name] as Record<string, unknown>) ?? {}, required.has(name)));
}

function toField(name: string, property: JsonSchema, ui: Record<string, unknown>, required: boolean): FieldModel {
  const options = (ui['ui:options'] as Record<string, unknown> | undefined) ?? {};
  const choices = choicesOf(property, ui);
  return {
    name,
    label: (ui['ui:title'] as string) ?? property.title ?? name,
    kind: kindOf(property, ui, choices),
    required,
    readOnly: property.readOnly === true || ui['ui:readonly'] === true,
    help: (ui['ui:help'] as string) ?? property.description,
    choices,
    rows: typeof options.rows === 'number' ? options.rows : undefined,
    minLength: property.minLength,
    maxLength: property.maxLength,
    minimum: property.minimum,
    maximum: property.maximum,
  };
}

function kindOf(property: JsonSchema, ui: Record<string, unknown>, choices: Choice[] | undefined): FieldKind {
  if (choices) {
    return 'choice';
  }
  switch (property.type) {
    case 'boolean':
      return 'boolean';
    case 'integer':
      return 'integer';
    case 'number':
      return 'number';
    default:
      if (property.format === 'date') {
        return 'date';
      }
      if (property.format === 'date-time') {
        return 'datetime';
      }
      return ui['ui:widget'] === 'textarea' ? 'textarea' : 'text';
  }
}

function choicesOf(property: JsonSchema, ui: Record<string, unknown>): Choice[] | undefined {
  if (Array.isArray(property.oneOf) && property.oneOf.every((option) => 'const' in option)) {
    return property.oneOf.map((option) => ({
      value: option.const as Choice['value'],
      label: option.title ?? String(option.const),
    }));
  }
  if (Array.isArray(property.enum)) {
    const names = ui['ui:enumNames'] as string[] | undefined;
    return property.enum.map((value, index) => ({
      value: value as Choice['value'],
      label: names?.[index] ?? String(value),
    }));
  }
  return undefined;
}

/** RJSF ui:order semantics: listed names first, "*" stands for all the rest in schema order. */
export function orderNames(names: string[], order: string[] | undefined): string[] {
  if (!order || order.length === 0) {
    return names;
  }
  const listed = order.filter((name) => name !== '*' && names.includes(name));
  const rest = names.filter((name) => !listed.includes(name));
  const star = order.indexOf('*');
  if (star === -1) {
    return [...listed, ...rest];
  }
  const before = order.slice(0, star).filter((name) => names.includes(name));
  const after = order.slice(star + 1).filter((name) => names.includes(name));
  return [...before, ...rest, ...after];
}

export function isEmpty(value: unknown): boolean {
  return value === undefined || value === null || value === '';
}

/** Client-side checks for quick feedback; returns field name → message. */
export function validate(fields: FieldModel[], data: FormData): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of fields) {
    if (field.readOnly) {
      continue;
    }
    const value = data[field.name];
    if (isEmpty(value)) {
      if (field.required) {
        errors[field.name] = 'This field is required.';
      }
      continue;
    }
    if ((field.kind === 'number' || field.kind === 'integer') && typeof value !== 'number') {
      errors[field.name] = 'Enter a number.';
    } else if (field.kind === 'integer' && !Number.isInteger(value)) {
      errors[field.name] = 'Enter a whole number.';
    } else if (typeof value === 'number' && field.minimum !== undefined && value < field.minimum) {
      errors[field.name] = `Must be at least ${field.minimum}.`;
    } else if (typeof value === 'number' && field.maximum !== undefined && value > field.maximum) {
      errors[field.name] = `Must be at most ${field.maximum}.`;
    } else if (typeof value === 'string' && field.minLength !== undefined && value.length < field.minLength) {
      errors[field.name] = `Enter at least ${field.minLength} characters.`;
    } else if (typeof value === 'string' && field.maxLength !== undefined && value.length > field.maxLength) {
      errors[field.name] = `Enter at most ${field.maxLength} characters.`;
    } else if (field.choices && !field.choices.some((choice) => choice.value === value)) {
      errors[field.name] = 'Choose one of the options.';
    }
  }
  return errors;
}

/**
 * The data to submit: empty values are dropped (as RJSF does) and only properties the schema declares are
 * sent. Read-only values are kept; the server ignores them when writing variables.
 */
export function submittable(schema: JsonSchema, data: FormData): FormData {
  const declared = new Set(Object.keys(schema.properties ?? {}));
  const result: FormData = {};
  for (const [name, value] of Object.entries(data)) {
    if (declared.has(name) && !isEmpty(value)) {
      result[name] = value;
    }
  }
  return result;
}
