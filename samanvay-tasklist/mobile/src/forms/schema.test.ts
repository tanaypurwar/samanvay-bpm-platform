import example from '../../../examples/invoice-approval.form.json';
import type { JsonSchema, UiSchema } from '../api/types';
import { buildFields, matches, orderNames, resolveConditions, submittable, validate } from './schema';

const schema = example.schema as unknown as JsonSchema;
const uiSchema = example.uiSchema as UiSchema;
const byName = (fields: ReturnType<typeof buildFields>) => Object.fromEntries(fields.map((f) => [f.name, f]));

describe('buildFields (example invoice form)', () => {
  it('follows ui:order and maps types to native widgets', () => {
    const fields = buildFields(schema, uiSchema, {});

    expect(fields.map((f) => f.name)).toEqual([
      'invoiceNumber', 'vendor', 'amount', 'invoiceDate', 'decision', 'costCenter', 'paymentDueDate', 'comment',
    ]);
    const f = byName(fields);
    expect(f.invoiceNumber).toMatchObject({ kind: 'text', readOnly: true });
    expect(f.amount).toMatchObject({ kind: 'number', readOnly: true });
    expect(f.invoiceDate.kind).toBe('date');
    expect(f.decision).toMatchObject({ kind: 'choice', required: true });
    expect(f.decision.choices).toEqual([
      { value: 'approve', label: 'Approve' },
      { value: 'reject', label: 'Reject' },
    ]);
    expect(f.comment).toMatchObject({ kind: 'textarea', rows: 4, maxLength: 1000 });
    expect(f.comment.help).toContain('Required when rejecting');
  });

  it('applies the conditional rules for the chosen decision', () => {
    const approve = byName(buildFields(schema, uiSchema, { decision: 'approve' }));
    expect(approve.costCenter.required).toBe(true);
    expect(approve.paymentDueDate.required).toBe(true);
    expect(approve.comment.required).toBe(false);

    const reject = byName(buildFields(schema, uiSchema, { decision: 'reject' }));
    expect(reject.costCenter.required).toBe(false);
    expect(reject.comment).toMatchObject({ required: true, minLength: 10 });
  });
});

describe('validate', () => {
  it('reports missing required and conditional fields', () => {
    const data = { decision: 'reject', comment: 'short' };
    expect(validate(buildFields(schema, uiSchema, data), data)).toEqual({
      comment: 'Enter at least 10 characters.',
    });
    expect(validate(buildFields(schema, uiSchema, {}), {})).toEqual({ decision: 'This field is required.' });
  });

  it('accepts a complete approval and ignores read-only fields', () => {
    const data = { invoiceNumber: '', decision: 'approve', costCenter: 'IT', paymentDueDate: '2026-11-15' };
    expect(validate(buildFields(schema, uiSchema, data), data)).toEqual({});
  });

  it('checks numbers and choices', () => {
    const numeric: JsonSchema = {
      type: 'object',
      properties: { qty: { type: 'integer', minimum: 1 }, size: { type: 'string', enum: ['S', 'M'] } },
    };
    const fields = buildFields(numeric, undefined, {});
    expect(validate(fields, { qty: 'abc' })).toEqual({ qty: 'Enter a number.' });
    expect(validate(fields, { qty: 1.5 })).toEqual({ qty: 'Enter a whole number.' });
    expect(validate(fields, { qty: 0 })).toEqual({ qty: 'Must be at least 1.' });
    expect(validate(fields, { size: 'XL' })).toEqual({ size: 'Choose one of the options.' });
  });
});

describe('conditions', () => {
  it('treats absent properties as matching, like JSON Schema', () => {
    expect(matches({ properties: { decision: { const: 'reject' } } }, {})).toBe(true);
    expect(matches({ properties: { decision: { const: 'reject' } }, required: ['decision'] }, {})).toBe(false);
    expect(matches({ properties: { size: { enum: ['S', 'M'] } } }, { size: 'L' })).toBe(false);
  });

  it('supports top-level if/then/else', () => {
    const conditional: JsonSchema = {
      type: 'object',
      properties: { urgent: { type: 'boolean' }, reason: { type: 'string' }, note: { type: 'string' } },
      if: { properties: { urgent: { const: true } }, required: ['urgent'] },
      then: { required: ['reason'] },
      else: { required: ['note'] },
    };
    expect([...resolveConditions(conditional, { urgent: true }).required]).toEqual(['reason']);
    expect([...resolveConditions(conditional, {}).required]).toEqual(['note']);
  });
});

describe('orderNames', () => {
  it('supports the "*" wildcard', () => {
    expect(orderNames(['a', 'b', 'c', 'd'], ['c', '*', 'a'])).toEqual(['c', 'b', 'd', 'a']);
    expect(orderNames(['a', 'b'], undefined)).toEqual(['a', 'b']);
  });
});

describe('submittable', () => {
  it('drops empty values and properties the schema does not declare', () => {
    expect(submittable(schema, { decision: 'approve', comment: '', extra: 'x', costCenter: 'IT' })).toEqual({
      decision: 'approve',
      costCenter: 'IT',
    });
  });
});
