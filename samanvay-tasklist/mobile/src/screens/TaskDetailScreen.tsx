import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from 'react-native';
import { ActivityIndicator, Banner, Button, Card, Chip, Divider, HelperText, Snackbar, Text, useTheme } from 'react-native-paper';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ApiError, errorMessage } from '../api/client';
import { claimTask, completeTask, fetchTask, fetchTaskForm, unclaimTask } from '../api/endpoints';
import type { FormData, Task } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { FormRenderer } from '../forms/FormRenderer';
import { buildFields, submittable, validate } from '../forms/schema';
import type { RootStackParamList } from '../navigation';
import { formatDateTime, isOverdue } from '../utils/format';

type Props = NativeStackScreenProps<RootStackParamList, 'Task'>;

export function TaskDetailScreen({ route, navigation }: Props) {
  const { id } = route.params;
  const { user } = useAuth();
  const theme = useTheme();
  const queryClient = useQueryClient();

  const task = useQuery({ queryKey: ['task', id], queryFn: () => fetchTask(id) });
  const form = useQuery({ queryKey: ['task', id, 'form'], queryFn: () => fetchTaskForm(id) });

  // kept here so the user's input survives a failed submission
  const [formData, setFormData] = useState<FormData>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [attempted, setAttempted] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    if (form.data?.formData) {
      setFormData(form.data.formData);
    }
  }, [form.data]);

  const schema = form.data?.schema;
  const fields = useMemo(() => (schema ? buildFields(schema, form.data?.uiSchema, formData) : []), [schema, form.data?.uiSchema, formData]);

  // after the first submit attempt, re-check as the user fixes things
  useEffect(() => {
    if (attempted) {
      setErrors(validate(fields, formData));
    }
  }, [attempted, fields, formData]);

  const onTaskChanged = (updated: Task, message: string) => {
    queryClient.setQueryData(['task', id], updated);
    queryClient.invalidateQueries({ queryKey: ['tasks'] });
    setActionError(null);
    setToast(message);
  };

  const claim = useMutation({
    mutationFn: () => claimTask(id),
    onSuccess: (updated) => onTaskChanged(updated, 'Task claimed. You can now work on it.'),
    onError: (e) => {
      setActionError(errorMessage(e));
      task.refetch();
    },
  });

  const unclaim = useMutation({
    mutationFn: () => unclaimTask(id),
    onSuccess: (updated) => onTaskChanged(updated, 'Task released.'),
    onError: (e) => setActionError(errorMessage(e)),
  });

  const complete = useMutation({
    mutationFn: (data: FormData) => completeTask(id, form.data?.formVersionId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      queryClient.removeQueries({ queryKey: ['task', id] });
      navigation.popTo('Inbox', { toast: `"${task.data?.name ?? 'Task'}" completed.` });
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === 'VALIDATION_FAILED') {
        // the server is the authority on validation (including rules the app does not check itself)
        setErrors(Object.fromEntries(e.fieldErrors.map((f) => [f.field, f.message])));
      }
      setActionError(errorMessage(e));
    },
  });

  const submit = () => {
    setAttempted(true);
    if (schema) {
      const found = validate(fields, formData);
      setErrors(found);
      if (Object.keys(found).length > 0) {
        setActionError('Please correct the highlighted fields.');
        return;
      }
      complete.mutate(submittable(schema, formData));
    } else {
      complete.mutate({});
    }
  };

  if (task.isPending || form.isPending) {
    return <ActivityIndicator style={styles.loading} />;
  }

  if (task.isError) {
    const gone = task.error instanceof ApiError && task.error.status === 404;
    return (
      <View style={styles.padded}>
        <Text variant="bodyLarge">
          {gone ? 'This task no longer exists or is not available to you. It may have been completed by someone else.' : errorMessage(task.error)}
        </Text>
        <Button mode="outlined" onPress={() => navigation.goBack()} style={styles.topGap}>
          Back to tasks
        </Button>
      </View>
    );
  }

  const t = task.data;
  const mine = t.assignee === user?.id;
  const busy = claim.isPending || unclaim.isPending || complete.isPending;

  return (
    <SafeAreaView edges={['bottom']} style={[styles.flex, { backgroundColor: theme.colors.background }]}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <Card mode="outlined">
            <Card.Content style={styles.gap}>
              <Text variant="headlineSmall" style={styles.title}>
                {t.name ?? 'Untitled task'}
              </Text>
              {t.description ? <Text variant="bodyMedium">{t.description}</Text> : null}
              <View style={styles.chips}>
                <Chip compact icon={mine ? 'account-check' : t.assignee ? 'account' : 'account-question'}>
                  {mine ? 'Assigned to you' : t.assignee ? t.assignee : 'Unassigned'}
                </Chip>
                {t.due ? (
                  <Chip compact icon="calendar-clock" textStyle={isOverdue(t.due) ? { color: theme.colors.error } : undefined}>
                    {isOverdue(t.due) ? 'Overdue ' : 'Due '}
                    {formatDateTime(t.due)}
                  </Chip>
                ) : null}
                {t.priority !== undefined ? (
                  <Chip compact icon="flag-outline">
                    Priority {t.priority}
                  </Chip>
                ) : null}
              </View>
              <Text variant="bodySmall" style={styles.muted}>
                Created {formatDateTime(t.created)}
              </Text>
            </Card.Content>
            {(!t.assignee || mine) && (
              <Card.Actions>
                {!t.assignee && (
                  <Button mode="contained" onPress={() => claim.mutate()} loading={claim.isPending} disabled={busy}>
                    Claim
                  </Button>
                )}
                {mine && (
                  <Button mode="outlined" onPress={() => unclaim.mutate()} loading={unclaim.isPending} disabled={busy}>
                    Release
                  </Button>
                )}
              </Card.Actions>
            )}
          </Card>

          <Banner visible={!mine} icon="information-outline" style={styles.banner}>
            {t.assignee ? 'This task is assigned to someone else.' : 'Claim this task to fill in the form and complete it.'}
          </Banner>

          {actionError && (
            <HelperText type="error" visible style={styles.actionError}>
              {actionError}
            </HelperText>
          )}

          <Card mode="outlined">
            <Card.Content style={styles.gap}>
              {form.isError && <HelperText type="error" visible>{errorMessage(form.error)}</HelperText>}
              {schema ? (
                <FormRenderer
                  schema={schema}
                  uiSchema={form.data?.uiSchema}
                  formData={formData}
                  onChange={(data) => {
                    setFormData(data);
                    if (actionError) {
                      setActionError(null);
                    }
                  }}
                  errors={errors}
                  disabled={!mine || busy}
                />
              ) : (
                <Variables variables={form.data?.variables ?? {}} />
              )}
            </Card.Content>
          </Card>
        </ScrollView>

        <Divider />
        <View style={[styles.footer, { backgroundColor: theme.colors.surface }]}>
          <Button mode="contained" onPress={submit} loading={complete.isPending} disabled={!mine || busy} contentStyle={styles.footerButton}>
            Complete task
          </Button>
        </View>
      </KeyboardAvoidingView>

      <Snackbar visible={toast !== null} onDismiss={() => setToast(null)} duration={3000}>
        {toast ?? ''}
      </Snackbar>
    </SafeAreaView>
  );
}

function Variables({ variables }: { variables: Record<string, unknown> }) {
  const entries = Object.entries(variables);
  if (entries.length === 0) {
    return <Text style={styles.muted}>This task has no additional details.</Text>;
  }
  return (
    <View style={styles.gap}>
      <Text variant="titleMedium">Details</Text>
      {entries.map(([name, value]) => (
        <View key={name}>
          <Text variant="bodySmall" style={styles.muted}>
            {name}
          </Text>
          <Text variant="bodyLarge">
            {value === null || value === undefined ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value)}
          </Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  loading: { marginTop: 48 },
  padded: { padding: 24 },
  topGap: { marginTop: 16 },
  content: { padding: 12, gap: 12, paddingBottom: 24 },
  gap: { gap: 10 },
  title: { fontWeight: '700' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  muted: { opacity: 0.7 },
  banner: { borderRadius: 12 },
  actionError: { paddingHorizontal: 4 },
  footer: { padding: 12 },
  footerButton: { paddingVertical: 6 },
});
