import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { useEffect, useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
import { HelperText, RadioButton, Switch, Text, TextInput, useTheme } from 'react-native-paper';
import type { FormData, JsonSchema, UiSchema } from '../api/types';
import { fromIsoDate, toIsoDate } from '../utils/format';
import { buildFields, type FieldModel } from './schema';

interface FormRendererProps {
  schema: JsonSchema;
  uiSchema?: UiSchema;
  formData: FormData;
  onChange: (data: FormData) => void;
  /** Field name → message, from client checks or from the server's 422 response. */
  errors: Record<string, string>;
  disabled?: boolean;
}

/** Native renderer for the same JSON Schema forms the web app renders with RJSF. */
export function FormRenderer({ schema, uiSchema, formData, onChange, errors, disabled }: FormRendererProps) {
  // fields depend on the data because conditional rules can make fields required
  const fields = useMemo(() => buildFields(schema, uiSchema, formData), [schema, uiSchema, formData]);
  const readOnly = fields.filter((field) => field.readOnly);
  const editable = fields.filter((field) => !field.readOnly);
  const set = (name: string, value: unknown) => onChange({ ...formData, [name]: value });

  return (
    <View style={styles.form}>
      {schema.title ? <Text variant="titleMedium">{schema.title}</Text> : null}
      {readOnly.length > 0 && (
        <View style={styles.summary}>
          {readOnly.map((field) => (
            <View key={field.name} style={styles.summaryRow}>
              <Text variant="bodySmall" style={styles.summaryLabel}>
                {field.label}
              </Text>
              <Text variant="bodyLarge">{displayValue(field, formData[field.name])}</Text>
            </View>
          ))}
        </View>
      )}
      {editable.map((field) => (
        <Field
          key={field.name}
          field={field}
          value={formData[field.name]}
          error={errors[field.name]}
          disabled={disabled}
          onChange={(value) => set(field.name, value)}
        />
      ))}
    </View>
  );
}

interface FieldProps {
  field: FieldModel;
  value: unknown;
  error?: string;
  disabled?: boolean;
  onChange: (value: unknown) => void;
}

function Field({ field, value, error, disabled, onChange }: FieldProps) {
  const label = field.required ? `${field.label} *` : field.label;
  const helper = error ? (
    <HelperText type="error" visible>
      {error}
    </HelperText>
  ) : field.help ? (
    <HelperText type="info" visible>
      {field.help}
    </HelperText>
  ) : null;

  switch (field.kind) {
    case 'choice':
      return (
        <View>
          <Text variant="labelLarge" style={styles.groupLabel}>
            {label}
          </Text>
          <RadioButton.Group onValueChange={(v) => onChange(field.choices!.find((c) => String(c.value) === v)?.value)} value={value === undefined ? '' : String(value)}>
            {field.choices!.map((choice) => (
              <RadioButton.Item
                key={String(choice.value)}
                label={choice.label}
                value={String(choice.value)}
                disabled={disabled}
                position="leading"
                style={styles.radio}
                labelStyle={styles.radioLabel}
              />
            ))}
          </RadioButton.Group>
          {helper}
        </View>
      );
    case 'boolean':
      return (
        <View>
          <View style={styles.switchRow}>
            <Text variant="bodyLarge" style={styles.flex}>
              {label}
            </Text>
            <Switch value={value === true} onValueChange={onChange} disabled={disabled} accessibilityLabel={field.label} />
          </View>
          {helper}
        </View>
      );
    case 'date':
    case 'datetime':
      return <DateField field={field} label={label} value={value} disabled={disabled} onChange={onChange} helper={helper} error={!!error} />;
    case 'number':
    case 'integer':
      return <NumberField field={field} label={label} value={value} disabled={disabled} onChange={onChange} helper={helper} error={!!error} />;
    default:
      return (
        <View>
          <TextInput
            mode="outlined"
            label={label}
            value={typeof value === 'string' ? value : ''}
            onChangeText={(text) => onChange(text === '' ? undefined : text)}
            disabled={disabled}
            error={!!error}
            multiline={field.kind === 'textarea'}
            numberOfLines={field.kind === 'textarea' ? field.rows ?? 4 : 1}
            maxLength={field.maxLength}
            style={field.kind === 'textarea' ? { minHeight: 24 * (field.rows ?? 4) } : undefined}
          />
          {helper}
        </View>
      );
  }
}

/** Keeps the typed text locally so partial input like "12." is not lost while converting to a number. */
function NumberField({ field, label, value, disabled, onChange, helper, error }: Omit<FieldProps, 'error'> & { label: string; helper: JSX.Element | null; error: boolean }) {
  const [text, setText] = useState(typeof value === 'number' ? String(value) : '');
  // follow changes made from outside (e.g. data reloaded), but keep partial input such as "12." or "abc"
  useEffect(() => {
    const typed = text.trim() === '' ? undefined : Number(text.replace(',', '.'));
    if (typeof value !== 'string' && value !== typed) {
      setText(typeof value === 'number' ? String(value) : '');
    }
  }, [value]);

  return (
    <View>
      <TextInput
        mode="outlined"
        label={label}
        value={text}
        keyboardType={field.kind === 'integer' ? 'number-pad' : 'decimal-pad'}
        onChangeText={(next) => {
          setText(next);
          const normalized = next.replace(',', '.').trim();
          if (normalized === '') {
            onChange(undefined);
          } else {
            const parsed = Number(normalized);
            onChange(Number.isNaN(parsed) ? normalized : parsed);
          }
        }}
        disabled={disabled}
        error={error}
      />
      {helper}
    </View>
  );
}

function DateField({ field, label, value, disabled, onChange, helper, error }: Omit<FieldProps, 'error'> & { label: string; helper: JSX.Element | null; error: boolean }) {
  const theme = useTheme();
  const [picking, setPicking] = useState<'date' | 'time' | null>(null);
  const current = toDate(field, value);

  const onPicked = (event: DateTimePickerEvent, picked?: Date) => {
    if (event.type !== 'set' || !picked) {
      setPicking(null);
      return;
    }
    if (field.kind === 'date') {
      setPicking(null);
      onChange(toIsoDate(picked));
    } else if (picking === 'date') {
      const base = current ?? new Date();
      const merged = new Date(picked.getFullYear(), picked.getMonth(), picked.getDate(), base.getHours(), base.getMinutes());
      onChange(merged.toISOString());
      setPicking(Platform.OS === 'android' ? 'time' : null);
    } else {
      const base = current ?? new Date();
      const merged = new Date(base.getFullYear(), base.getMonth(), base.getDate(), picked.getHours(), picked.getMinutes());
      setPicking(null);
      onChange(merged.toISOString());
    }
  };

  const shown = current
    ? field.kind === 'date'
      ? current.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
      : `${current.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}, ${current.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}`
    : '';

  return (
    <View>
      <Pressable onPress={() => !disabled && setPicking('date')} accessibilityRole="button" accessibilityLabel={`${field.label}, choose date`}>
        <View pointerEvents="none">
          <TextInput
            mode="outlined"
            label={label}
            value={shown}
            editable={false}
            disabled={disabled}
            error={error}
            right={<TextInput.Icon icon="calendar" color={theme.colors.primary} />}
          />
        </View>
      </Pressable>
      {picking && (
        <DateTimePicker
          value={current ?? new Date()}
          mode={picking}
          onChange={onPicked}
        />
      )}
      {helper}
    </View>
  );
}

function toDate(field: FieldModel, value: unknown): Date | null {
  if (typeof value !== 'string' || value === '') {
    return null;
  }
  if (field.kind === 'date') {
    return fromIsoDate(value);
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function displayValue(field: FieldModel, value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  if (field.choices) {
    return field.choices.find((choice) => choice.value === value)?.label ?? String(value);
  }
  if (field.kind === 'date' && typeof value === 'string') {
    const date = fromIsoDate(value);
    return date ? date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : value;
  }
  if (field.kind === 'boolean') {
    return value === true ? 'Yes' : 'No';
  }
  if (typeof value === 'number') {
    return value.toLocaleString();
  }
  return String(value);
}

const styles = StyleSheet.create({
  form: { gap: 14 },
  summary: { gap: 10, paddingVertical: 4 },
  summaryRow: { gap: 2 },
  summaryLabel: { opacity: 0.7 },
  groupLabel: { marginBottom: 2 },
  radio: { paddingVertical: 2, paddingHorizontal: 0 },
  radioLabel: { textAlign: 'left' },
  switchRow: { flexDirection: 'row', alignItems: 'center', minHeight: 48 },
  flex: { flex: 1 },
});
