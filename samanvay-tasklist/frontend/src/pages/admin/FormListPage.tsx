import AddIcon from '@mui/icons-material/Add';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { createForm, fetchForms } from '../../api/forms';
import { formatDateTime } from '../../utils/format';

const STARTER_SCHEMA = {
  title: 'New form',
  type: 'object',
  required: ['comment'],
  properties: {
    comment: { type: 'string', title: 'Comment' },
  },
};

export function FormListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const forms = useQuery({ queryKey: ['admin-forms'], queryFn: fetchForms });
  const [open, setOpen] = useState(false);
  const [formKey, setFormKey] = useState('');
  const [name, setName] = useState('');

  const create = useMutation({
    mutationFn: () => createForm({ formKey: formKey.trim(), name: name.trim(), schema: STARTER_SCHEMA, uiSchema: {} }),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['admin-forms'] });
      navigate(`/admin/forms/${created.id}`);
    },
  });

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
        <Typography variant="h1">Forms</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
          New form
        </Button>
      </Box>
      <Typography color="text.secondary">
        Reference a form from BPMN with <code>camunda:formKey="samanvay:&lt;form key&gt;"</code>, or bind it to a
        task definition key on the form page.
      </Typography>

      {forms.isError && <Alert severity="error">{errorMessage(forms.error)}</Alert>}
      <Paper sx={{ overflowX: 'auto' }}>
        {forms.isFetching && <LinearProgress />}
        <Table aria-label="Forms">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Form key</TableCell>
              <TableCell>Status</TableCell>
              <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>Updated</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {forms.data?.map((form) => (
              <TableRow key={form.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/forms/${form.id}`)}>
                <TableCell sx={{ fontWeight: 600 }}>{form.name}</TableCell>
                <TableCell>
                  <code>{form.formKey}</code>
                </TableCell>
                <TableCell>
                  {form.latestVersion ? (
                    <Chip size="small" color="success" label={`v${form.latestVersion} published`} />
                  ) : (
                    <Chip size="small" variant="outlined" label="Draft" />
                  )}
                </TableCell>
                <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{formatDateTime(form.updatedAt)}</TableCell>
              </TableRow>
            ))}
            {forms.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
                  No forms yet.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>New form</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {create.isError && <Alert severity="error">{errorMessage(create.error)}</Alert>}
            <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} autoFocus fullWidth />
            <TextField
              label="Form key"
              value={formKey}
              onChange={(e) => setFormKey(e.target.value)}
              helperText="Used in BPMN as samanvay:<form key>. Letters, digits, '.', '_' and '-'."
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => create.mutate()}
            disabled={!formKey.trim() || !name.trim() || create.isPending}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
