import { Alert, Box, Button, CircularProgress, Paper, Stack, TextField, Typography } from '@mui/material';
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useBranding } from '../theme/BrandingContext';

export function LoginPage() {
  const { user, login, sessionExpired } = useAuth();
  const branding = useBranding();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to={from} replace />;
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(username.trim(), password);
      navigate(from, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
      setPassword('');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: 'background.default', px: 2 }}>
      <Paper component="form" onSubmit={submit} sx={{ p: { xs: 3, sm: 4 }, width: '100%', maxWidth: 400 }} noValidate>
        <Stack spacing={2.5}>
          <Box>
            <Typography variant="h1" component="h1">
              {branding.name} {branding.product}
            </Typography>
            <Typography color="text.secondary">Sign in to see your tasks</Typography>
          </Box>
          {sessionExpired && !error && <Alert severity="info">Your session has expired. Please sign in again.</Alert>}
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            required
            fullWidth
          />
          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
            fullWidth
          />
          <Button
            type="submit"
            variant="contained"
            size="large"
            disabled={submitting || !username.trim() || !password}
            startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : undefined}
          >
            Sign in
          </Button>
        </Stack>
      </Paper>
    </Box>
  );
}
