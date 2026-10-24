import { Box, Button, CircularProgress } from '@mui/material';
import Form from '@rjsf/mui';
import type { ErrorSchema, RJSFSchema, UiSchema } from '@rjsf/utils';
import validator from '@rjsf/validator-ajv8';
import { useMemo, useState } from 'react';
import type { FieldError } from '../api/client';
import type { FormData } from '../api/types';

interface DynamicFormProps {
  schema: RJSFSchema;
  uiSchema?: UiSchema;
  formData: FormData;
  onChange: (data: FormData) => void;
  onSubmit?: (data: FormData) => void;
  /** Server-side validation errors to show next to the fields. */
  serverErrors?: FieldError[];
  disabled?: boolean;
  submitting?: boolean;
  submitLabel?: string;
}

/**
 * Renders a configured JSON Schema form with RJSF + MUI. Validates on submit, then live as the user fixes
 * errors. The parent owns the form data so input survives failed submissions.
 */
export function DynamicForm({
  schema,
  uiSchema,
  formData,
  onChange,
  onSubmit,
  serverErrors,
  disabled = false,
  submitting = false,
  submitLabel = 'Submit',
}: DynamicFormProps) {
  const [attempted, setAttempted] = useState(false);
  const extraErrors = useMemo(() => toErrorSchema(serverErrors), [serverErrors]);

  return (
    <Form
      schema={schema}
      uiSchema={uiSchema}
      formData={formData}
      validator={validator}
      disabled={disabled || submitting}
      liveValidate={attempted}
      showErrorList={false}
      noHtml5Validate
      focusOnFirstError
      extraErrors={extraErrors}
      onChange={(event) => onChange((event.formData ?? {}) as FormData)}
      onError={() => setAttempted(true)}
      onSubmit={(event) => {
        setAttempted(true);
        onSubmit?.((event.formData ?? {}) as FormData);
      }}
    >
      {onSubmit ? (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2 }}>
          <Button
            type="submit"
            variant="contained"
            size="large"
            disabled={disabled || submitting}
            startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : undefined}
            sx={{ width: { xs: '100%', sm: 'auto' } }}
          >
            {submitLabel}
          </Button>
        </Box>
      ) : (
        <></>
      )}
    </Form>
  );
}

/** Converts backend field errors ("a.b") into RJSF's nested error schema. */
export function toErrorSchema(errors?: FieldError[]): ErrorSchema | undefined {
  if (!errors || errors.length === 0) {
    return undefined;
  }
  const root: Record<string, unknown> = {};
  for (const error of errors) {
    const path = error.field ? error.field.split('.') : [];
    let node = root;
    for (const segment of path) {
      node[segment] = (node[segment] as Record<string, unknown>) ?? {};
      node = node[segment] as Record<string, unknown>;
    }
    const list = (node.__errors as string[]) ?? [];
    list.push(error.message);
    node.__errors = list;
  }
  return root as ErrorSchema;
}
