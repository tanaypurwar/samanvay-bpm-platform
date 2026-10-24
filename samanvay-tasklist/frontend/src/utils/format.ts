/** Parses engine dates like "2026-10-23T10:15:30.000+0000" (offset without colon, which Safari rejects). */
export function parseEngineDate(value?: string | null): Date | null {
  if (!value) {
    return null;
  }
  const normalized = value.replace(/([+-]\d{2})(\d{2})$/, '$1:$2');
  const date = new Date(normalized);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(value?: string | null): string {
  const date = parseEngineDate(value);
  return date ? date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '';
}

export function formatDate(value?: string | null): string {
  const date = parseEngineDate(value);
  return date ? date.toLocaleDateString(undefined, { dateStyle: 'medium' }) : '';
}

export function isOverdue(due?: string | null, now: Date = new Date()): boolean {
  const date = parseEngineDate(due);
  return date !== null && date.getTime() < now.getTime();
}

const relative = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

export function formatRelative(value?: string | null, now: Date = new Date()): string {
  const date = parseEngineDate(value);
  if (!date) {
    return '';
  }
  const minutes = Math.round((date.getTime() - now.getTime()) / 60000);
  if (Math.abs(minutes) < 60) {
    return relative.format(minutes, 'minute');
  }
  const hours = Math.round(minutes / 60);
  if (Math.abs(hours) < 24) {
    return relative.format(hours, 'hour');
  }
  return relative.format(Math.round(hours / 24), 'day');
}
