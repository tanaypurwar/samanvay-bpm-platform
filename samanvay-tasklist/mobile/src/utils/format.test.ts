import { formatRelative, fromIsoDate, isOverdue, parseEngineDate, toIsoDate } from './format';

describe('format', () => {
  it('parses the engine REST date format', () => {
    expect(parseEngineDate('2026-10-23T10:15:30.000+0530')?.toISOString()).toBe('2026-10-23T04:45:30.000Z');
    expect(parseEngineDate('nope')).toBeNull();
  });

  it('round-trips local calendar dates', () => {
    const date = fromIsoDate('2026-11-05')!;
    expect(date.getDate()).toBe(5);
    expect(toIsoDate(date)).toBe('2026-11-05');
    expect(fromIsoDate('05/11/2026')).toBeNull();
  });

  it('describes relative times', () => {
    const now = new Date('2026-10-23T12:00:00Z');
    expect(formatRelative('2026-10-23T11:55:00.000+0000', now)).toBe('5 min ago');
    expect(formatRelative('2026-10-23T09:00:00.000+0000', now)).toBe('3 h ago');
    expect(formatRelative('2026-10-25T12:00:00.000+0000', now)).toBe('in 2 d');
  });

  it('flags overdue tasks', () => {
    const now = new Date('2026-10-23T12:00:00Z');
    expect(isOverdue('2026-10-22T12:00:00.000+0000', now)).toBe(true);
    expect(isOverdue(undefined, now)).toBe(false);
  });
});
