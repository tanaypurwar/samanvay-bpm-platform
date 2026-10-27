import { Box, Dialog, useMediaQuery, useTheme } from '@mui/material';
import type { ReactNode } from 'react';

interface Props {
  /** The pane beside the content, e.g. a document preview; null when closed. */
  side: ReactNode | null;
  onCloseSide: () => void;
  children: ReactNode;
}

/**
 * The page content with an optional pane on the right that stays in view while the content scrolls, so a
 * reviewer can compare a document with the form. On small screens the pane opens full screen instead.
 */
export function SplitView({ side, onCloseSide, children }: Props) {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('md'));

  if (!wide) {
    return (
      <>
        <Box sx={{ maxWidth: 1200, mx: 'auto' }}>{children}</Box>
        <Dialog fullScreen open={side !== null} onClose={onCloseSide}>
          <Box sx={{ height: '100%', p: 1 }}>{side}</Box>
        </Dialog>
      </>
    );
  }

  return (
    <Box
      sx={{
        display: 'grid',
        gap: 3,
        alignItems: 'start',
        gridTemplateColumns: side ? 'minmax(0, 1fr) minmax(0, 1fr)' : 'minmax(0, 1fr)',
        maxWidth: side ? 'none' : 1200,
        mx: 'auto',
      }}
    >
      <Box sx={{ minWidth: 0 }}>{children}</Box>
      {side && (
        <Box sx={{ position: 'sticky', top: 88, height: 'calc(100vh - 112px)', minWidth: 0 }}>{side}</Box>
      )}
    </Box>
  );
}
