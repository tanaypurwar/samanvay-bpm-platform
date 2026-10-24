import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Grid2 as Grid,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Paper,
  Snackbar,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import type { RJSFSchema, UiSchema } from '@rjsf/utils';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Component, useEffect, useState, type ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { addBinding, fetchForm, publishForm, removeBinding, saveDraft } from '../../api/forms';
import type { FormData, FormDetail } from '../../api/types';
import { DynamicForm } from '../../components/DynamicForm';
import { JsonEditor } from '../../components/JsonEditor';
import { formatDateTime } from '../../utils/format';

export function FormEditorPage() {
  const id = Number(useParams().id);
  const queryClient = useQueryClient();
  const form = useQuery({ queryKey: ['admin-form', id], queryFn: () => fetchForm(id) });

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [schema, setSchema] = useState<RJSFSchema | undefined>();
  const [uiSchema, setUiSchema] = useState<UiSchema | undefined>();
  const [schemaValid, setSchemaValid] = useState(true);
  const [uiValid, setUiValid] = useState(true);
  const [tab, setTab] = useState<'schema' | 'ui'>('schema');
  const [previewData, setPreviewData] = useState<FormData>({});
  const [confirmPublish, setConfirmPublish] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (form.data) {
      setName(form.data.name);
      setDescription(form.data.description ?? '');
      setSchema(form.data.draftSchema);
      setUiSchema(form.data.draftUiSchema);
    }
  }, [form.data]);

  const onSaved = (detail: FormDetail, message: string) => {
    queryClient.setQueryData(['admin-form', id], detail);
    queryClient.invalidateQueries({ queryKey: ['admin-forms'] });
    setError(null);
    setToast(message);
  };

  const save = useMutation({
    mutationFn: () => saveDraft(id, { name: name.trim(), description: description.trim() || undefined, schema, uiSchema }),
    onSuccess: (detail) => onSaved(detail, 'Draft saved.'),
    onError: (e) => setError(errorMessage(e)),
  });

  const publish = useMutation({
    mutationFn: async () => {
      await saveDraft(id, { name: name.trim(), description: description.trim() || undefined, schema, uiSchema });
      return publishForm(id);
    },
    onSuccess: (detail) => onSaved(detail, `Published version ${detail.versions[0]?.version}. New tasks use it now.`),
    onError: (e) => setError(errorMessage(e)),
    onSettled: () => setConfirmPublish(false),
  });

  if (form.isPending) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '40vh' }}>
        <CircularProgress aria-label="Loading form" />
      </Box>
    );
  }
  if (form.isError) {
    return <Alert severity="error">{errorMessage(form.error)}</Alert>;
  }

  const detail = form.data;
  const canSave = schemaValid && uiValid && name.trim().length > 0;

  return (
    <Stack spacing={2}>
      <Box>
        <Button component={RouterLink} to="/admin/forms" startIcon={<ArrowBackIcon />} size="small">
          All forms
        </Button>
      </Box>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="h1">{detail.name}</Typography>
          <Typography color="text.secondary">
            <code>samanvay:{detail.formKey}</code>
            {detail.versions[0] && ` · latest published v${detail.versions[0].version}`}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => save.mutate()} disabled={!canSave || save.isPending}>
            Save draft
          </Button>
          <Button variant="contained" onClick={() => setConfirmPublish(true)} disabled={!canSave || publish.isPending}>
            Publish
          </Button>
        </Stack>
      </Stack>

      {error && (
        <Alert severity="error" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2 }}>
            <Stack spacing={2}>
              <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} fullWidth required />
              <TextField
                label="Description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                fullWidth
              />
              <Tabs value={tab} onChange={(_, v) => setTab(v)}>
                <Tab value="schema" label="JSON Schema" />
                <Tab value="ui" label="UI Schema" />
              </Tabs>
              <Box hidden={tab !== 'schema'}>
                <JsonEditor
                  label="Schema (JSON Schema draft-07)"
                  value={schema}
                  onChange={(value, valid) => {
                    setSchemaValid(valid);
                    if (valid) {
                      setSchema(value as RJSFSchema | undefined);
                    }
                  }}
                />
              </Box>
              <Box hidden={tab !== 'ui'}>
                <JsonEditor
                  label="UI schema (RJSF)"
                  value={uiSchema}
                  onChange={(value, valid) => {
                    setUiValid(valid);
                    if (valid) {
                      setUiSchema(value as UiSchema | undefined);
                    }
                  }}
                />
              </Box>
              <Typography variant="body2" color="text.secondary">
                Field names are the process variable names. Mark display-only fields <code>"readOnly": true</code>;
                set <code>"x-camunda-type"</code> to override the variable type.
              </Typography>
            </Stack>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h3" sx={{ mb: 1 }}>
              Preview
            </Typography>
            {schema ? (
              <PreviewBoundary key={JSON.stringify(schema) + JSON.stringify(uiSchema ?? {})}>
                <DynamicForm
                  schema={schema}
                  uiSchema={uiSchema}
                  formData={previewData}
                  onChange={setPreviewData}
                  onSubmit={() => setToast('Preview data is valid.')}
                  submitLabel="Validate preview"
                />
              </PreviewBoundary>
            ) : (
              <Typography color="text.secondary">Add a schema to see the form.</Typography>
            )}
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <BindingsPanel detail={detail} onChanged={(message) => {
            queryClient.invalidateQueries({ queryKey: ['admin-form', id] });
            setToast(message);
          }} />
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h3">Published versions</Typography>
            {detail.versions.length === 0 ? (
              <Typography color="text.secondary" sx={{ mt: 1 }}>
                Not published yet. Tasks cannot use this form until it is published.
              </Typography>
            ) : (
              <List dense>
                {detail.versions.map((v, index) => (
                  <ListItem key={v.id} disableGutters>
                    <ListItemText
                      primary={
                        <>
                          Version {v.version} {index === 0 && <Chip size="small" color="success" label="live" sx={{ ml: 1 }} />}
                        </>
                      }
                      secondary={`${formatDateTime(v.publishedAt)} by ${v.publishedBy}`}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </Paper>
        </Grid>
      </Grid>

      <Dialog open={confirmPublish} onClose={() => setConfirmPublish(false)}>
        <DialogTitle>Publish this form?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            The draft becomes version {(detail.versions[0]?.version ?? 0) + 1}. Tasks start using it immediately;
            published versions cannot be changed later.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmPublish(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => publish.mutate()} disabled={publish.isPending}>
            Publish
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={toast !== null} autoHideDuration={4000} onClose={() => setToast(null)} message={toast} />
    </Stack>
  );
}

function BindingsPanel({ detail, onChanged }: { detail: FormDetail; onChanged: (message: string) => void }) {
  const [taskKey, setTaskKey] = useState('');
  const [processKey, setProcessKey] = useState('');
  const [priority, setPriority] = useState('0');
  const [error, setError] = useState<string | null>(null);

  const add = useMutation({
    mutationFn: () =>
      addBinding(detail.id, {
        taskDefinitionKey: taskKey.trim(),
        processDefinitionKey: processKey.trim() || undefined,
        priority: Number.parseInt(priority, 10) || 0,
      }),
    onSuccess: () => {
      setTaskKey('');
      setProcessKey('');
      setPriority('0');
      setError(null);
      onChanged('Binding added.');
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const remove = useMutation({
    mutationFn: (bindingId: number) => removeBinding(detail.id, bindingId),
    onSuccess: () => onChanged('Binding removed.'),
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h3">Task bindings</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
        Used for user tasks whose BPMN has no <code>samanvay:</code> form key.
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mt: 1 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      <List dense>
        {detail.bindings.map((b) => (
          <ListItem
            key={b.id}
            disableGutters
            secondaryAction={
              <IconButton edge="end" aria-label="Remove binding" onClick={() => remove.mutate(b.id)}>
                <DeleteOutlineIcon />
              </IconButton>
            }
          >
            <ListItemText
              primary={<code>{b.taskDefinitionKey}</code>}
              secondary={`${b.processDefinitionKey ? `process ${b.processDefinitionKey}` : 'any process'} · priority ${b.priority}`}
            />
          </ListItem>
        ))}
      </List>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
        <TextField size="small" label="Task definition key" value={taskKey} onChange={(e) => setTaskKey(e.target.value)} fullWidth />
        <TextField size="small" label="Process key (optional)" value={processKey} onChange={(e) => setProcessKey(e.target.value)} fullWidth />
        <TextField
          size="small"
          label="Priority"
          type="number"
          value={priority}
          onChange={(e) => setPriority(e.target.value)}
          sx={{ width: { sm: 110 } }}
        />
        <Button variant="outlined" onClick={() => add.mutate()} disabled={!taskKey.trim() || add.isPending}>
          Add
        </Button>
      </Stack>
    </Paper>
  );
}

/** Keeps a broken schema in the editor from crashing the whole page. */
class PreviewBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  render() {
    if (this.state.error) {
      return <Alert severity="warning">The preview cannot render this schema: {this.state.error.message}</Alert>;
    }
    return this.props.children;
  }
}
