import AttachFileIcon from '@mui/icons-material/AttachFile';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import {
  Alert,
  Box,
  Button,
  Chip,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useRef, useState } from 'react';
import type { DocumentOptions } from '../api/types';

export interface DocumentItem {
  key: string;
  fileName: string;
  typeLabel: string;
  contentType: string;
  size: number;
  /** e.g. who attached it, at which step and when */
  detail?: string;
  removable?: boolean;
}

interface Props {
  options: DocumentOptions;
  items: DocumentItem[];
  selectedKey?: string | null;
  onOpen: (key: string) => void;
  /** Absent when the user may not attach documents now. */
  onAttach?: (file: File, documentType: string) => void;
  onRemove?: (key: string) => void;
  attaching?: boolean;
  /** Shown instead of the attach controls when attaching is not possible. */
  attachHint?: string;
  error?: string | null;
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Client-side check before uploading; the server checks the content again. */
export function checkFile(file: File, options: DocumentOptions): string | null {
  if (file.type && !options.contentTypes.includes(file.type)) {
    return `"${file.name}" is not a PDF, JPEG or PNG file.`;
  }
  if (file.size > options.maxFileSize) {
    return `"${file.name}" is larger than ${formatSize(options.maxFileSize)}.`;
  }
  return null;
}

/**
 * The documents attached to a process: open one to preview it beside the form, and attach more with their
 * document type.
 */
export function DocumentsPanel({
  options,
  items,
  selectedKey,
  onOpen,
  onAttach,
  onRemove,
  attaching,
  attachHint,
  error,
}: Props) {
  const [documentType, setDocumentType] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);

  const pick = (file: File | undefined) => {
    if (!file || !onAttach) {
      return;
    }
    const problem = checkFile(file, options);
    setLocalError(problem);
    if (!problem) {
      onAttach(file, documentType);
    }
  };

  return (
    <Stack spacing={1.5}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Typography variant="h3">Documents</Typography>
        <Chip size="small" label={items.length} />
      </Stack>

      {items.length === 0 ? (
        <Typography color="text.secondary">No documents attached yet.</Typography>
      ) : (
        <List dense disablePadding sx={{ border: 1, borderColor: 'divider', borderRadius: 1 }}>
          {items.map((item, i) => (
            <ListItem
              key={item.key}
              disablePadding
              divider={i < items.length - 1}
              secondaryAction={
                item.removable && onRemove ? (
                  <Tooltip title="Remove">
                    <IconButton edge="end" aria-label={`Remove ${item.fileName}`} onClick={() => onRemove(item.key)}>
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                ) : undefined
              }
            >
              <ListItemButton selected={item.key === selectedKey} onClick={() => onOpen(item.key)}>
                <ListItemIcon sx={{ minWidth: 36 }}>
                  {item.contentType === 'application/pdf' ? (
                    <PictureAsPdfOutlinedIcon color="error" />
                  ) : (
                    <ImageOutlinedIcon color="primary" />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Box component="span" sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                      <Chip size="small" color="secondary" variant="outlined" label={item.typeLabel} />
                      <Box component="span" sx={{ wordBreak: 'break-all' }}>
                        {item.fileName}
                      </Box>
                    </Box>
                  }
                  secondary={[formatSize(item.size), item.detail].filter(Boolean).join(' · ')}
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      )}

      {(error || localError) && (
        <Alert severity="error" onClose={() => setLocalError(null)}>
          {localError ?? error}
        </Alert>
      )}

      {onAttach ? (
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
          <TextField
            select
            size="small"
            label="Document type"
            value={documentType}
            onChange={(e) => setDocumentType(e.target.value)}
            sx={{ minWidth: 240 }}
          >
            {options.types.map((t) => (
              <MenuItem key={t.id} value={t.id}>
                {t.label}
              </MenuItem>
            ))}
          </TextField>
          <Button
            variant="outlined"
            startIcon={<AttachFileIcon />}
            disabled={!documentType || attaching}
            onClick={() => input.current?.click()}
          >
            {attaching ? 'Attaching…' : 'Attach file'}
          </Button>
          <Typography variant="body2" color="text.secondary">
            PDF, JPEG or PNG, up to {formatSize(options.maxFileSize)}
          </Typography>
          <input
            ref={input}
            type="file"
            hidden
            accept={options.contentTypes.join(',')}
            data-testid="document-file-input"
            onChange={(e) => {
              pick(e.target.files?.[0]);
              e.target.value = '';
            }}
          />
        </Stack>
      ) : (
        attachHint && (
          <Typography variant="body2" color="text.secondary">
            {attachHint}
          </Typography>
        )
      )}
    </Stack>
  );
}
