import { describe, expect, it } from 'vitest';
import { parseTaskQuery } from './TaskInboxPage';

describe('parseTaskQuery', () => {
  it('defaults to all tasks, newest first, first page', () => {
    expect(parseTaskQuery(new URLSearchParams())).toEqual({
      scope: 'all',
      q: '',
      sort: 'created',
      order: 'desc',
      page: 0,
      size: 20,
    });
  });

  it('reads filters, sort and page from the URL', () => {
    expect(parseTaskQuery(new URLSearchParams('scope=mine&q=invoice&sort=dueDate:asc&page=2'))).toMatchObject({
      scope: 'mine',
      q: 'invoice',
      sort: 'dueDate',
      order: 'asc',
      page: 2,
    });
  });

  it('ignores invalid values', () => {
    expect(parseTaskQuery(new URLSearchParams('scope=everyone&sort=password:asc&page=-3'))).toMatchObject({
      scope: 'all',
      sort: 'created',
      order: 'desc',
      page: 0,
    });
  });
});
