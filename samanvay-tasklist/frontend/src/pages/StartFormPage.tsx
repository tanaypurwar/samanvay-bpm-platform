import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { ApiError, errorMessage, type FieldError } from '../api/client';
import { fetchStartForm, startProcess } from '../api/processes';
import type { FormData } from '../api/types';
import { DocumentPreview } from '../components/DocumentPreview';
import { DocumentsPanel } from '../components/DocumentsPanel';
import { DynamicForm } from '../components/DynamicForm';
import { SplitView } from '../components/SplitView';

/** A document chosen on this page; it is uploaded together with the form when the process starts. */
interface PendingDocument {
  key: string;
  file: File;
  documentType: string;
  typeLabel: string;
}

let nextKey = 0;

export function StartFormPage() {
  const { key = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const form = useQuery({ queryKey: ['process', key, 'start-form'], queryFn: () => fetchStartForm(key) });

  const [formData, setFormData] = useState<FormData>({});
  const [serverErrors, setServerErrors] = useState<FieldError[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [attachments, setAttachments] = useState<PendingDocument[]>([]);
  const [attachError, setAttachError] = useState<string | null>(null);
  const [previewKey, setPreviewKey] = useState<string | null>(null);

  const start = useMutation({
    mutationFn: (data: FormData) => startProcess(key, form.data?.formVersionId, data, attachments),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      navigate('/', { replace: true, state: { toast: `"${form.data?.processName ?? 'Request'}" started.` } });
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'VALIDATION_FAILED') {
        setServerErrors(e.fieldErrors);
      } else {
        setServerErrors([]);
      }
      setError(errorMessage(e));
    },
  });

  if (form.isPending) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '40vh' }}>
        <CircularProgress aria-label="Loading form" />
      </Box>
    );
  }

  const documents = form.data?.documents;
  const preview = attachments.find((a) => a.key === previewKey);

  const attach = (file: File, documentType: string) => {
    if (!documents) {
      return;
    }
    if (attachments.length >= documents.maxFiles) {
      setAttachError(`Attach at most ${documents.maxFiles} documents.`);
      return;
    }
    const added: PendingDocument = {
      key: `pending-${nextKey++}`,
      file,
      documentType,
      typeLabel: documents.types.find((t) => t.id === documentType)?.label ?? documentType,
    };
    setAttachError(null);
    setAttachments((current) => [...current, added]);
    setPreviewKey(added.key);
  };

  return (
    <SplitView
      onCloseSide={() => setPreviewKey(null)}
      side={
        preview ? (
          <DocumentPreview
            documentKey={preview.key}
            title={preview.file.name}
            subtitle={`${preview.typeLabel} · not submitted yet`}
            load={() => Promise.resolve(preview.file)}
            onClose={() => setPreviewKey(null)}
          />
        ) : null
      }
    >
      <Stack spacing={2}>
        <Box>
          <Button component={RouterLink} to="/start" startIcon={<ArrowBackIcon />} size="small">
            All requests
          </Button>
        </Box>

        {form.isError && (
          <Alert severity={form.error instanceof ApiError && form.error.status === 404 ? 'info' : 'error'}>
            {errorMessage(form.error)}
          </Alert>
        )}

        {form.data && (
          <>
            <Box>
              <Typography variant="h1">{form.data.formName ?? form.data.processName}</Typography>
              {form.data.formName && form.data.formName !== form.data.processName && (
                <Typography color="text.secondary">{form.data.processName}</Typography>
              )}
            </Box>
            {error && (
              <Alert severity="error" onClose={() => setError(null)}>
                {error}
              </Alert>
            )}
            {documents?.enabled && (
              <Paper sx={{ p: { xs: 2, sm: 3 } }}>
                <DocumentsPanel
                  options={documents}
                  items={attachments.map((a) => ({
                    key: a.key,
                    fileName: a.file.name,
                    typeLabel: a.typeLabel,
                    contentType: a.file.type,
                    size: a.file.size,
                    detail: 'attached when you submit',
                    removable: true,
                  }))}
                  selectedKey={previewKey}
                  onOpen={setPreviewKey}
                  onAttach={start.isPending ? undefined : attach}
                  onRemove={(removed) => {
                    setAttachments((current) => current.filter((a) => a.key !== removed));
                    setPreviewKey((current) => (current === removed ? null : current));
                  }}
                  error={attachError}
                />
              </Paper>
            )}
            <Paper sx={{ p: { xs: 2, sm: 3 } }}>
              {form.data.schema ? (
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
                  onSubmit={(data) => start.mutate(data)}
                  serverErrors={serverErrors}
                  submitting={start.isPending}
                  submitLabel="Start"
                />
              ) : (
                <Stack spacing={2}>
                  <Typography>This process needs no details to start.</Typography>
                  <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button variant="contained" size="large" disabled={start.isPending} onClick={() => start.mutate({})}>
                      Start
                    </Button>
                  </Box>
                </Stack>
              )}
            </Paper>
          </>
        )}
      </Stack>
    </SplitView>
  );
}
