import { describe, expect, it } from 'vitest';
import { isOverdue, parseEngineDate } from './format';

describe('parseEngineDate', () => {
  it('parses the engine REST date format with a colon-less offset', () => {
    expect(parseEngineDate('2026-10-23T10:15:30.000+0530')?.toISOString()).toBe('2026-10-23T04:45:30.000Z');
  });

  it('returns null for missing or invalid values', () => {
    expect(parseEngineDate(undefined)).toBeNull();
    expect(parseEngineDate('not a date')).toBeNull();
  });
});

describe('isOverdue', () => {
  it('compares against now', () => {
    const now = new Date('2026-10-23T12:00:00Z');
    expect(isOverdue('2026-10-23T11:00:00.000+0000', now)).toBe(true);
    expect(isOverdue('2026-10-24T11:00:00.000+0000', now)).toBe(false);
    expect(isOverdue(undefined, now)).toBe(false);
  });
});
