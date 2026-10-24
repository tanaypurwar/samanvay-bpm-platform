import {
  Box,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import type { Task } from '../api/types';
import { formatDate, formatRelative, isOverdue } from '../utils/format';

interface TaskListProps {
  tasks: Task[];
  currentUserId: string;
}

/** Table on wide screens, cards on phones (TRD FR-09). */
export function TaskList({ tasks, currentUserId }: TaskListProps) {
  const theme = useTheme();
  const compact = useMediaQuery(theme.breakpoints.down('md'));
  const navigate = useNavigate();
  const open = (task: Task) => navigate(`/tasks/${encodeURIComponent(task.id)}`);

  if (compact) {
    return (
      <Stack spacing={1.5}>
        {tasks.map((task) => (
          <Card key={task.id}>
            <CardActionArea onClick={() => open(task)}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'flex-start' }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 600, wordBreak: 'break-word' }}>
                    {task.name ?? 'Untitled task'}
                  </Typography>
                  <AssigneeChip task={task} currentUserId={currentUserId} />
                </Box>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                  Created {formatRelative(task.created)}
                </Typography>
                {task.due && <DueLabel due={task.due} />}
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
      </Stack>
    );
  }

  return (
    <Table size="medium" aria-label="Tasks">
      <TableHead>
        <TableRow>
          <TableCell>Task</TableCell>
          <TableCell>Assignee</TableCell>
          <TableCell>Created</TableCell>
          <TableCell>Due</TableCell>
          <TableCell align="right">Priority</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {tasks.map((task) => (
          <TableRow
            key={task.id}
            hover
            onClick={() => open(task)}
            onKeyDown={(e) => e.key === 'Enter' && open(task)}
            tabIndex={0}
            sx={{ cursor: 'pointer' }}
          >
            <TableCell>
              <Typography sx={{ fontWeight: 600 }}>{task.name ?? 'Untitled task'}</Typography>
              {task.description && (
                <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: 420 }}>
                  {task.description}
                </Typography>
              )}
            </TableCell>
            <TableCell>
              <AssigneeChip task={task} currentUserId={currentUserId} />
            </TableCell>
            <TableCell>{formatRelative(task.created)}</TableCell>
            <TableCell>{task.due ? <DueLabel due={task.due} /> : '—'}</TableCell>
            <TableCell align="right">{task.priority ?? '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function AssigneeChip({ task, currentUserId }: { task: Task; currentUserId: string }) {
  if (!task.assignee) {
    return <Chip size="small" label="Unassigned" variant="outlined" />;
  }
  if (task.assignee === currentUserId) {
    return <Chip size="small" label="You" color="primary" />;
  }
  return <Chip size="small" label={task.assignee} />;
}

function DueLabel({ due }: { due: string }) {
  const overdue = isOverdue(due);
  return (
    <Typography variant="body2" color={overdue ? 'error' : 'text.secondary'} component="span">
      {overdue ? 'Overdue · ' : 'Due '}
      {formatDate(due)}
    </Typography>
  );
}
