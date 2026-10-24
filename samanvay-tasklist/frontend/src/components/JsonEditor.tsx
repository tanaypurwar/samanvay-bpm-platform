import { TextField } from '@mui/material';
import { useEffect, useState } from 'react';

interface JsonEditorProps {
  label: string;
  value: unknown;
  onChange: (value: unknown, valid: boolean) => void;
  minRows?: number;
}

/**
 * Plain-text JSON editor with parse feedback. The text is kept locally so the user can type through
 * temporarily invalid JSON; the parsed value is reported whenever it is valid.
 */
export function JsonEditor({ label, value, onChange, minRows = 16 }: JsonEditorProps) {
  const [text, setText] = useState(() => stringify(value));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // only replace the text when the value was changed from outside (e.g. reloaded after save)
    setText((current) => (sameJson(current, value) ? current : stringify(value)));
  }, [value]);

  return (
    <TextField
      label={label}
      value={text}
      multiline
      minRows={minRows}
      fullWidth
      error={error !== null}
      helperText={error ?? ' '}
      spellCheck={false}
      slotProps={{ input: { sx: { fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace', fontSize: 13 } } }}
      onChange={(event) => {
        const next = event.target.value;
        setText(next);
        if (next.trim() === '') {
          setError(null);
          onChange(undefined, true);
          return;
        }
        try {
          const parsed = JSON.parse(next);
          setError(null);
          onChange(parsed, true);
        } catch (e) {
          setError(`Invalid JSON: ${(e as Error).message}`);
          onChange(undefined, false);
        }
      }}
    />
  );
}

function stringify(value: unknown): string {
  return value === undefined || value === null ? '' : JSON.stringify(value, null, 2);
}

function safeParse(text: string): unknown {
  try {
    return text.trim() === '' ? null : JSON.parse(text);
  } catch {
    return undefined;
  }
}

function sameJson(text: string, value: unknown): boolean {
  return JSON.stringify(safeParse(text) ?? null) === JSON.stringify(value ?? null);
}
