/** Parses engine dates like "2026-10-23T10:15:30.000+0000" (offset without a colon). */
export function parseEngineDate(value?: string | null): Date | null {
  if (!value) {
    return null;
  }
  const date = new Date(value.replace(/([+-]\d{2})(\d{2})$/, '$1:$2'));
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDate(value?: string | null): string {
  const date = parseEngineDate(value);
  return date ? date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '';
}

export function formatDateTime(value?: string | null): string {
  const date = parseEngineDate(value);
  return date
    ? `${date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}, ${date
        .toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`
    : '';
}

export function isOverdue(due?: string | null, now: Date = new Date()): boolean {
  const date = parseEngineDate(due);
  return date !== null && date.getTime() < now.getTime();
}

/** "5 min ago", "3 h ago", "2 d ago": Intl.RelativeTimeFormat is not available on every Android JS engine. */
export function formatRelative(value?: string | null, now: Date = new Date()): string {
  const date = parseEngineDate(value);
  if (!date) {
    return '';
  }
  const minutes = Math.round((now.getTime() - date.getTime()) / 60000);
  const future = minutes < 0;
  const abs = Math.abs(minutes);
  const text = abs < 1 ? 'just now' : abs < 60 ? `${abs} min` : abs < 1440 ? `${Math.round(abs / 60)} h` : `${Math.round(abs / 1440)} d`;
  if (text === 'just now') {
    return text;
  }
  return future ? `in ${text}` : `${text} ago`;
}

/** Local calendar date as YYYY-MM-DD (the JSON Schema "date" format). */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Parses YYYY-MM-DD as a local calendar date. */
export function fromIsoDate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : null;
}
