import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Link, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AppShell } from './AppShell';

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'rm1', firstName: 'Ravi', lastName: 'M', admin: false }, logout: vi.fn() }),
}));
vi.mock('../api/processes', () => ({ fetchStartableProcesses: () => Promise.resolve([]) }));

describe('AppShell', () => {
  it('keeps rendering when navigating from the inbox to a task', async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route element={<AppShell />}>
              <Route path="/" element={<Link to="/tasks/t1">Open task</Link>} />
              <Route path="/tasks/:id" element={<p>Task page</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await userEvent.click(screen.getByText('Open task'));

    expect(await screen.findByText('Task page')).toBeInTheDocument();
  });
});
