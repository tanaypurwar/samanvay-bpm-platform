import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Paper,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { ApiError, errorMessage, type FieldError } from '../api/client';
import { deleteTaskDocument, fetchDocumentContent, fetchTaskDocuments, uploadTaskDocument } from '../api/documents';
import { claimTask, completeTask, fetchTask, fetchTaskForm, unclaimTask } from '../api/tasks';
import type { FormData, Task } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { DocumentPreview } from '../components/DocumentPreview';
import { DocumentsPanel } from '../components/DocumentsPanel';
import { DynamicForm } from '../components/DynamicForm';
import { SplitView } from '../components/SplitView';
import { formatDateTime, isOverdue } from '../utils/format';

export function TaskDetailPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const task = useQuery({ queryKey: ['task', id], queryFn: () => fetchTask(id) });
  const form = useQuery({ queryKey: ['task', id, 'form'], queryFn: () => fetchTaskForm(id) });

  // form data lives here so user input survives failed submissions (TRD FR-07)
  const [formData, setFormData] = useState<FormData>({});
  const [serverErrors, setServerErrors] = useState<FieldError[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    if (form.data?.formData) {
      setFormData(form.data.formData);
    }
  }, [form.data]);

  const documents = useQuery({ queryKey: ['task', id, 'documents'], queryFn: () => fetchTaskDocuments(id) });
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [documentError, setDocumentError] = useState<string | null>(null);
  const refreshDocuments = () => queryClient.invalidateQueries({ queryKey: ['task', id, 'documents'], exact: true });

  const attach = useMutation({
    mutationFn: ({ file, documentType }: { file: File; documentType: string }) =>
      uploadTaskDocument(id, file, documentType),
    onSuccess: (document) => {
      setDocumentError(null);
      refreshDocuments();
      setPreviewId(document.id);
      setToast(`"${document.fileName}" attached.`);
    },
    onError: (e) => setDocumentError(errorMessage(e)),
  });

  const removeDocument = useMutation({
    mutationFn: (documentId: string) => deleteTaskDocument(id, documentId),
    onSuccess: (_, documentId) => {
      setDocumentError(null);
      setPreviewId((current) => (current === documentId ? null : current));
      refreshDocuments();
    },
    onError: (e) => setDocumentError(errorMessage(e)),
  });

  const onTaskChanged = (updated: Task) => {
    queryClient.setQueryData(['task', id], updated);
    queryClient.invalidateQueries({ queryKey: ['tasks'] });
    // attaching depends on who has the task
    refreshDocuments();
  };

  const claim = useMutation({
    mutationFn: () => claimTask(id),
    onSuccess: (updated) => {
      setActionError(null);
      onTaskChanged(updated);
      setToast('Task claimed. You can now work on it.');
    },
    onError: (e) => {
      setActionError(errorMessage(e));
      task.refetch();
    },
  });

  const unclaim = useMutation({
    mutationFn: () => unclaimTask(id),
    onSuccess: (updated) => {
      setActionError(null);
      onTaskChanged(updated);
      setToast('Task released.');
    },
    onError: (e) => setActionError(errorMessage(e)),
  });

  const complete = useMutation({
    mutationFn: (data: FormData) => completeTask(id, form.data?.formVersionId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      queryClient.removeQueries({ queryKey: ['task', id] });
      navigate('/', { replace: true, state: { toast: `"${task.data?.name ?? 'Task'}" completed.` } });
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'VALIDATION_FAILED') {
        setServerErrors(e.fieldErrors);
        setActionError(e.message);
      } else {
        setServerErrors([]);
        setActionError(errorMessage(e));
      }
    },
  });

  if (task.isPending || form.isPending) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '40vh' }}>
        <CircularProgress aria-label="Loading task" />
      </Box>
    );
  }

  if (task.isError) {
    return (
      <Stack spacing={2}>
        <BackLink />
        <Alert severity={task.error instanceof ApiError && task.error.status === 404 ? 'info' : 'error'}>
          {task.error instanceof ApiError && task.error.status === 404
            ? 'This task no longer exists or is not available to you. It may have been completed by someone else.'
            : errorMessage(task.error)}
        </Alert>
      </Stack>
    );
  }

  const t = task.data;
  const mine = t.assignee === user?.id;
  const busy = claim.isPending || unclaim.isPending || complete.isPending;
  const hasForm = Boolean(form.data?.schema);
  const docs = documents.data;
  const previewDocument = docs?.documents.find((d) => d.id === previewId);

  return (
    <SplitView
      onCloseSide={() => setPreviewId(null)}
      side={
        previewDocument ? (
          <DocumentPreview
            documentKey={`${id}/${previewDocument.id}`}
            title={previewDocument.fileName ?? 'Document'}
            subtitle={[previewDocument.typeLabel, previewDocument.uploadedByName].filter(Boolean).join(' · ')}
            load={() => fetchDocumentContent(id, previewDocument.id)}
            onClose={() => setPreviewId(null)}
          />
        ) : null
      }
    >
      <Stack spacing={2}>
        <BackLink />

        <Paper sx={{ p: { xs: 2, sm: 3 } }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between' }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="h1" sx={{ wordBreak: 'break-word' }}>
                {t.name ?? 'Untitled task'}
              </Typography>
              {t.description && (
                <Typography color="text.secondary" sx={{ mt: 0.5 }}>
                  {t.description}
                </Typography>
              )}
              <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1.5, flexWrap: 'wrap' }}>
                <Chip
                  size="small"
                  color={mine ? 'primary' : 'default'}
                  variant={t.assignee ? 'filled' : 'outlined'}
                  label={mine ? 'Assigned to you' : t.assignee ? `Assigned to ${t.assignee}` : 'Unassigned'}
                />
                {t.due && (
                  <Chip
                    size="small"
                    color={isOverdue(t.due) ? 'error' : 'default'}
                    label={`${isOverdue(t.due) ? 'Overdue' : 'Due'} ${formatDateTime(t.due)}`}
                  />
                )}
                <Chip size="small" variant="outlined" label={`Created ${formatDateTime(t.created)}`} />
                {t.priority !== undefined && <Chip size="small" variant="outlined" label={`Priority ${t.priority}`} />}
              </Stack>
            </Box>
            <Box sx={{ flexShrink: 0 }}>
              {!t.assignee && (
                <Button variant="contained" onClick={() => claim.mutate()} disabled={busy} fullWidth>
                  Claim
                </Button>
              )}
              {mine && (
                <Button variant="outlined" onClick={() => unclaim.mutate()} disabled={busy} fullWidth>
                  Release
                </Button>
              )}
            </Box>
          </Stack>
        </Paper>

        {actionError && (
          <Alert severity="error" onClose={() => setActionError(null)}>
            {actionError}
          </Alert>
        )}
        {!mine && (
          <Alert severity="info">
            {t.assignee ? 'This task is assigned to someone else.' : 'Claim this task to fill in the form and complete it.'}
          </Alert>
        )}

        {documents.isError && <Alert severity="warning">Documents could not be loaded: {errorMessage(documents.error)}</Alert>}
        {docs?.options.enabled && (
          <Paper sx={{ p: { xs: 2, sm: 3 } }}>
            <DocumentsPanel
              options={docs.options}
              items={docs.documents.map((d) => ({
                key: d.id,
                fileName: d.fileName ?? 'Document',
                typeLabel: d.typeLabel ?? d.type ?? 'Document',
                contentType: d.contentType,
                size: d.size,
                detail: [d.uploadedByName, d.step, formatDateTime(d.uploadedAt)].filter(Boolean).join(' · '),
                removable: d.deletable,
              }))}
              selectedKey={previewId}
              onOpen={setPreviewId}
              onAttach={
                docs.canUpload ? (file, documentType) => attach.mutate({ file, documentType }) : undefined
              }
              onRemove={(documentId) => removeDocument.mutate(documentId)}
              attaching={attach.isPending}
              attachHint={!t.assignee ? 'Claim this task to attach documents.' : undefined}
              error={documentError}
            />
          </Paper>
        )}

        <Paper sx={{ p: { xs: 2, sm: 3 } }}>
          {form.isError && <Alert severity="error">{errorMessage(form.error)}</Alert>}

          {hasForm && form.data?.schema && (
            <DynamicForm
              schema={form.data.schema}
              uiSchema={form.data.uiSchema}
              formData={formData}
              onChange={(data) => {
                setFormData(data);
                if (serverErrors.length) {
                  setServerErrors([]);
                }
              }}
              onSubmit={(data) => complete.mutate(data)}
              serverErrors={serverErrors}
              disabled={!mine || busy}
              submitting={complete.isPending}
              submitLabel="Complete task"
            />
          )}

          {!hasForm && form.data && (
            <Stack spacing={2}>
              <Typography variant="h3">Details</Typography>
              <VariablesTable variables={form.data.variables ?? {}} />
              <Divider />
              <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                <Button
                  variant="contained"
                  size="large"
                  disabled={!mine || busy}
                  onClick={() => complete.mutate({})}
                  sx={{ width: { xs: '100%', sm: 'auto' } }}
                >
                  Complete task
                </Button>
              </Box>
            </Stack>
          )}
        </Paper>

        <Snackbar open={toast !== null} autoHideDuration={4000} onClose={() => setToast(null)} message={toast} />
      </Stack>
    </SplitView>
  );
}

function BackLink() {
  return (
    <Box>
      <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />} size="small">
        All tasks
      </Button>
    </Box>
  );
}

function VariablesTable({ variables }: { variables: Record<string, unknown> }) {
  const entries = Object.entries(variables);
  if (entries.length === 0) {
    return <Typography color="text.secondary">This task has no additional details.</Typography>;
  }
  return (
    <Table size="small">
      <TableBody>
        {entries.map(([name, value]) => (
          <TableRow key={name}>
            <TableCell component="th" sx={{ fontWeight: 600, width: '40%', wordBreak: 'break-word' }}>
              {name}
            </TableCell>
            <TableCell sx={{ wordBreak: 'break-word' }}>
              {value === null || value === undefined ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
