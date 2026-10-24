import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import {
  Alert,
  Box,
  IconButton,
  InputAdornment,
  LinearProgress,
  MenuItem,
  Pagination,
  Snackbar,
  Paper,
  Stack,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { fetchTasks } from '../api/tasks';
import type { Scope, SortField, TaskQuery } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { TaskList } from '../components/TaskList';

const PAGE_SIZE = 20;
const SCOPES: { value: Scope; label: string }[] = [
  { value: 'mine', label: 'My tasks' },
  { value: 'group', label: 'Available' },
  { value: 'all', label: 'All' },
];
const SORTS: { value: string; label: string }[] = [
  { value: 'created:desc', label: 'Newest first' },
  { value: 'created:asc', label: 'Oldest first' },
  { value: 'dueDate:asc', label: 'Due date' },
  { value: 'priority:desc', label: 'Priority' },
  { value: 'name:asc', label: 'Name' },
];

/** Reads the inbox query from the URL so filters survive reloads, back navigation and sharing. */
export function parseTaskQuery(params: URLSearchParams): TaskQuery {
  const scope = (['mine', 'group', 'all'] as Scope[]).find((s) => s === params.get('scope')) ?? 'all';
  const [sort, order] = (params.get('sort') ?? 'created:desc').split(':');
  const validSort = SORTS.some((s) => s.value === `${sort}:${order}`);
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0);
  return {
    scope,
    q: params.get('q') ?? '',
    sort: (validSort ? sort : 'created') as SortField,
    order: validSort && order === 'asc' ? 'asc' : 'desc',
    page,
    size: PAGE_SIZE,
  };
}

export function TaskInboxPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const query = parseTaskQuery(params);
  const [search, setSearch] = useState(query.q);
  const location = useLocation();
  const navigate = useNavigate();
  // confirmation passed from the task page after completing a task
  const [toast, setToast] = useState<string | null>((location.state as { toast?: string } | null)?.toast ?? null);

  useEffect(() => {
    if (location.state) {
      navigate({ search: location.search }, { replace: true, state: null });
    }
  }, [location.state, location.search, navigate]);

  const update = (changes: Record<string, string | null>, resetPage = true) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)));
    if (resetPage) {
      next.delete('page');
    }
    setParams(next, { replace: true });
  };

  // debounce search typing into the URL
  useEffect(() => {
    if (search === query.q) {
      return;
    }
    const timer = setTimeout(() => update({ q: search.trim() || null }), 350);
    return () => clearTimeout(timer);
  }, [search]);

  const tasks = useQuery({
    queryKey: ['tasks', query],
    queryFn: () => fetchTasks(query),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });

  const pageCount = tasks.data ? Math.max(1, Math.ceil(tasks.data.total / PAGE_SIZE)) : 1;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
        <Typography variant="h1">Tasks</Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={() => tasks.refetch()} aria-label="Refresh tasks">
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Box>

      <Paper sx={{ overflow: 'hidden' }}>
        <Tabs
          value={query.scope}
          onChange={(_, value: Scope) => update({ scope: value === 'all' ? null : value })}
          variant="fullWidth"
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          {SCOPES.map((s) => (
            <Tab key={s.value} value={s.value} label={s.label} />
          ))}
        </Tabs>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ p: 2 }}>
          <TextField
            placeholder="Search by task name"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            size="small"
            fullWidth
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              },
              htmlInput: { 'aria-label': 'Search tasks' },
            }}
          />
          <TextField
            select
            size="small"
            label="Sort"
            value={`${query.sort}:${query.order}`}
            onChange={(e) => update({ sort: e.target.value === 'created:desc' ? null : e.target.value })}
            sx={{ minWidth: { sm: 180 } }}
          >
            {SORTS.map((s) => (
              <MenuItem key={s.value} value={s.value}>
                {s.label}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        {tasks.isFetching && <LinearProgress />}
      </Paper>

      {tasks.isError && <Alert severity="error">{errorMessage(tasks.error)}</Alert>}

      {tasks.data && tasks.data.items.length === 0 && (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h3">No tasks here</Typography>
          <Typography color="text.secondary" sx={{ mt: 0.5 }}>
            {query.q
              ? 'No tasks match your search.'
              : query.scope === 'mine'
                ? 'Claim a task from Available to start working on it.'
                : 'New tasks will appear here when processes reach you.'}
          </Typography>
        </Paper>
      )}

      {tasks.data && tasks.data.items.length > 0 && user && (
        <Paper sx={{ overflow: 'hidden', p: { xs: 1.5, md: 0 }, bgcolor: { xs: 'transparent', md: 'background.paper' }, border: { xs: 0, md: 1 }, borderColor: { md: 'divider' } }}>
          <TaskList tasks={tasks.data.items} currentUserId={user.id} />
        </Paper>
      )}

      {tasks.data && tasks.data.total > PAGE_SIZE && (
        <Box sx={{ display: 'flex', justifyContent: 'center' }}>
          <Pagination
            count={pageCount}
            page={query.page + 1}
            onChange={(_, page) => update({ page: page > 1 ? String(page - 1) : null }, false)}
            color="primary"
            siblingCount={0}
          />
        </Box>
      )}

      <Snackbar open={toast !== null} autoHideDuration={4000} onClose={() => setToast(null)} message={toast} />
    </Stack>
  );
}
