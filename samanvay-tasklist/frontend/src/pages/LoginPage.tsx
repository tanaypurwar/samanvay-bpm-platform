import { Alert, Box, Button, CircularProgress, Paper, Stack, TextField, Typography } from '@mui/material';
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { BrandMark } from '../components/BrandMark';
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
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: '5fr 6fr' },
        bgcolor: 'background.default',
      }}
    >
      {/* brand panel on wide screens; phones go straight to the form */}
      <Box
        sx={{
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'space-between',
          p: 6,
          color: '#fff',
          bgcolor: '#0e0e0e',
          backgroundImage: `radial-gradient(120% 80% at 0% 0%, ${branding.primaryColor}e6 0%, ${branding.primaryColor}66 45%, transparent 75%)`,
        }}
      >
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <BrandMark size={48} />
          <Typography sx={{ fontWeight: 700, fontSize: '1.15rem' }}>
            {branding.name}{' '}
            <Box component="span" sx={{ fontWeight: 500, opacity: 0.8 }}>
              {branding.product}
            </Box>
          </Typography>
        </Stack>
        <Box sx={{ maxWidth: 420 }}>
          <Typography sx={{ fontSize: '2.1rem', fontWeight: 700, lineHeight: 1.2, letterSpacing: '-0.02em' }}>
            Every request, approval and document in one place.
          </Typography>
          <Typography sx={{ mt: 2, opacity: 0.8 }}>
            Pick up your tasks, see what is waiting on you, and move work forward from any device.
          </Typography>
        </Box>
        <Typography variant="body2" sx={{ opacity: 0.6 }}>
          Powered by Samanvay BPM Platform
        </Typography>
      </Box>

      <Box sx={{ display: 'grid', placeItems: 'center', px: 2, py: 4 }}>
        <Paper
          component="form"
          onSubmit={submit}
          sx={{ p: { xs: 3, sm: 5 }, width: '100%', maxWidth: 420, boxShadow: '0 10px 30px rgba(15, 23, 42, 0.06)' }}
          noValidate
        >
          <Stack spacing={2.5}>
            <Stack
              spacing={2}
              sx={{ alignItems: { xs: 'center', md: 'flex-start' }, textAlign: { xs: 'center', md: 'left' } }}
            >
              <Box sx={{ display: { md: 'none' } }}>
                <BrandMark size={72} />
              </Box>
              <Box>
                <Typography variant="h1" component="h1">
                  Sign in
                </Typography>
                <Typography color="text.secondary" sx={{ mt: 0.5 }}>
                  to {branding.name} {branding.product}
                </Typography>
              </Box>
            </Stack>
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
    </Box>
  );
}
