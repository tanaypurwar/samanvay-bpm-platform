import CloseIcon from '@mui/icons-material/Close';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import { Alert, Box, CircularProgress, IconButton, Paper, Stack, Tooltip, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { errorMessage } from '../api/client';

interface Props {
  /** Identifies the document, for caching its content. */
  documentKey: string;
  title: string;
  subtitle?: string;
  load: () => Promise<Blob>;
  onClose: () => void;
}

/** Shows a PDF (the browser's viewer) or an image, so it can be checked against the form beside it. */
export function DocumentPreview({ documentKey, title, subtitle, load, onClose }: Props) {
  const content = useQuery({ queryKey: ['document-content', documentKey], queryFn: load, staleTime: Infinity, gcTime: 5 * 60_000 });
  const [url, setUrl] = useState<string | null>(null);
  const [actualSize, setActualSize] = useState(false);

  useEffect(() => {
    if (!content.data) {
      setUrl(null);
      return undefined;
    }
    const objectUrl = URL.createObjectURL(content.data);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [content.data]);

  useEffect(() => setActualSize(false), [documentKey]);

  const isPdf = content.data?.type === 'application/pdf';

  return (
    <Paper
      variant="outlined"
      sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
      aria-label={`Preview of ${title}`}
    >
      <Stack direction="row" spacing={1} sx={{ px: 2, py: 1, alignItems: 'center', borderBottom: 1, borderColor: 'divider' }}>
        <Box sx={{ minWidth: 0, flexGrow: 1 }}>
          <Typography variant="subtitle1" noWrap title={title} sx={{ fontWeight: 600 }}>
            {title}
          </Typography>
          {subtitle && (
            <Typography variant="body2" color="text.secondary" noWrap title={subtitle}>
              {subtitle}
            </Typography>
          )}
        </Box>
        {url && !isPdf && (
          <Tooltip title={actualSize ? 'Fit to pane' : 'Actual size'}>
            <IconButton onClick={() => setActualSize((v) => !v)} aria-label={actualSize ? 'Fit to pane' : 'Actual size'}>
              {actualSize ? <ZoomOutIcon /> : <ZoomInIcon />}
            </IconButton>
          </Tooltip>
        )}
        {url && (
          <Tooltip title="Open in a new tab">
            <IconButton component="a" href={url} target="_blank" rel="noopener" aria-label="Open in a new tab">
              <OpenInNewIcon />
            </IconButton>
          </Tooltip>
        )}
        <Tooltip title="Close preview">
          <IconButton onClick={onClose} aria-label="Close preview">
            <CloseIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      <Box sx={{ flexGrow: 1, minHeight: 0, bgcolor: 'action.hover', overflow: 'auto' }}>
        {content.isPending && (
          <Box sx={{ display: 'grid', placeItems: 'center', height: '100%' }}>
            <CircularProgress aria-label="Loading document" />
          </Box>
        )}
        {content.isError && (
          <Alert severity="error" sx={{ m: 2 }}>
            {errorMessage(content.error)}
          </Alert>
        )}
        {url && isPdf && (
          <Box component="iframe" src={url} title={title} sx={{ border: 0, width: '100%', height: '100%', display: 'block' }} />
        )}
        {url && !isPdf && (
          <Box
            component="img"
            src={url}
            alt={title}
            onClick={() => setActualSize((v) => !v)}
            sx={
              actualSize
                ? { display: 'block', cursor: 'zoom-out' }
                : { display: 'block', width: '100%', height: '100%', objectFit: 'contain', cursor: 'zoom-in' }
            }
          />
        )}
      </Box>
    </Paper>
  );
}
