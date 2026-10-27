import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import { Alert, Box, Card, CardActionArea, CardContent, CircularProgress, Stack, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { fetchStartableProcesses } from '../api/processes';

/** The processes the signed-in user may start. */
export function StartProcessPage() {
  const processes = useQuery({ queryKey: ['processes'], queryFn: fetchStartableProcesses });

  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="h1">Start a request</Typography>
        <Typography color="text.secondary">Choose what you want to start.</Typography>
      </Box>

      {processes.isPending && (
        <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '20vh' }}>
          <CircularProgress aria-label="Loading processes" />
        </Box>
      )}
      {processes.isError && <Alert severity="error">{errorMessage(processes.error)}</Alert>}
      {processes.data?.length === 0 && <Alert severity="info">There is nothing you can start.</Alert>}

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' },
        }}
      >
        {processes.data?.map((p) => (
          <Card key={p.key}>
            <CardActionArea component={RouterLink} to={`/start/${encodeURIComponent(p.key)}`} sx={{ height: '100%' }}>
              <CardContent>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
                  <PlayCircleOutlineIcon color="primary" />
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="h3">{p.name}</Typography>
                    {p.description && (
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        {p.description}
                      </Typography>
                    )}
                  </Box>
                </Stack>
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
      </Box>
    </Stack>
  );
}
