import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useState } from 'react';
import { FlatList, RefreshControl, StyleSheet, View } from 'react-native';
import {
  ActivityIndicator,
  Appbar,
  Card,
  Chip,
  Divider,
  HelperText,
  Menu,
  Searchbar,
  SegmentedButtons,
  Snackbar,
  Text,
  useTheme,
} from 'react-native-paper';
import { errorMessage } from '../api/client';
import { fetchTasks, PAGE_SIZE } from '../api/endpoints';
import type { Scope, Task } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import type { RootStackParamList } from '../navigation';
import { formatDate, formatRelative, isOverdue } from '../utils/format';

type Props = NativeStackScreenProps<RootStackParamList, 'Inbox'>;

export function InboxScreen({ navigation, route }: Props) {
  const { user, logout, serverUrl } = useAuth();
  const theme = useTheme();
  const [scope, setScope] = useState<Scope>('all');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  // confirmation passed back from the task screen after completing a task
  useEffect(() => {
    if (route.params?.toast) {
      setToast(route.params.toast);
      navigation.setParams({ toast: undefined });
    }
  }, [route.params?.toast, navigation]);

  // debounce search typing
  useEffect(() => {
    const timer = setTimeout(() => setQuery(search.trim()), 350);
    return () => clearTimeout(timer);
  }, [search]);

  useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <Menu
          visible={menuOpen}
          onDismiss={() => setMenuOpen(false)}
          anchor={<Appbar.Action icon="account-circle" color={theme.colors.onPrimary} onPress={() => setMenuOpen(true)} accessibilityLabel="Account" />}
        >
          <View style={styles.menuHeader}>
            <Text variant="titleSmall">{[user?.firstName, user?.lastName].filter(Boolean).join(' ') || user?.id}</Text>
            <Text variant="bodySmall" style={styles.muted}>
              {serverUrl}
            </Text>
          </View>
          <Divider />
          <Menu.Item
            leadingIcon="logout"
            title="Sign out"
            onPress={() => {
              setMenuOpen(false);
              logout();
            }}
          />
        </Menu>
      ),
    });
  }, [navigation, menuOpen, user, serverUrl, logout, theme.colors.onPrimary]);

  const tasks = useInfiniteQuery({
    queryKey: ['tasks', scope, query],
    queryFn: ({ pageParam }) => fetchTasks(scope, query, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => ((last.page + 1) * PAGE_SIZE < last.total ? last.page + 1 : undefined),
    refetchInterval: 60_000,
  });

  const items = tasks.data?.pages.flatMap((page) => page.items) ?? [];
  const total = tasks.data?.pages[0]?.total ?? 0;

  return (
    <View style={[styles.flex, { backgroundColor: theme.colors.background }]}>
      <View style={[styles.filters, { backgroundColor: theme.colors.surface }]}>
        <SegmentedButtons
          value={scope}
          onValueChange={(value) => setScope(value as Scope)}
          buttons={[
            { value: 'mine', label: 'Mine', icon: 'account' },
            { value: 'group', label: 'Available', icon: 'inbox' },
            { value: 'all', label: 'All' },
          ]}
        />
        <Searchbar placeholder="Search tasks" value={search} onChangeText={setSearch} style={styles.search} inputStyle={styles.searchInput} />
      </View>

      {tasks.isError && (
        <HelperText type="error" visible style={styles.error}>
          {errorMessage(tasks.error)}
        </HelperText>
      )}

      <FlatList
        data={items}
        keyExtractor={(task) => task.id}
        contentContainerStyle={styles.list}
        refreshControl={<RefreshControl refreshing={tasks.isRefetching && !tasks.isFetchingNextPage} onRefresh={() => tasks.refetch()} />}
        onEndReached={() => tasks.hasNextPage && !tasks.isFetchingNextPage && tasks.fetchNextPage()}
        onEndReachedThreshold={0.4}
        ListHeaderComponent={
          tasks.data ? (
            <Text variant="labelMedium" style={styles.count}>
              {total === 1 ? '1 task' : `${total} tasks`}
            </Text>
          ) : null
        }
        ListEmptyComponent={
          tasks.isPending ? (
            <ActivityIndicator style={styles.loading} />
          ) : tasks.isError ? null : (
            <View style={styles.empty}>
              <Text variant="titleMedium">No tasks here</Text>
              <Text variant="bodyMedium" style={styles.muted}>
                {query
                  ? 'No tasks match your search.'
                  : scope === 'mine'
                    ? 'Claim a task from Available to start working on it.'
                    : 'New tasks appear here when processes reach you.'}
              </Text>
            </View>
          )
        }
        ListFooterComponent={tasks.isFetchingNextPage ? <ActivityIndicator style={styles.loading} /> : null}
        renderItem={({ item }) => (
          <TaskCard task={item} currentUserId={user?.id ?? ''} onPress={() => navigation.navigate('Task', { id: item.id, name: item.name })} />
        )}
      />

      <Snackbar visible={toast !== null} onDismiss={() => setToast(null)} duration={4000}>
        {toast ?? ''}
      </Snackbar>
    </View>
  );
}

function TaskCard({ task, currentUserId, onPress }: { task: Task; currentUserId: string; onPress: () => void }) {
  const theme = useTheme();
  const overdue = isOverdue(task.due);
  const assignee = !task.assignee ? 'Unassigned' : task.assignee === currentUserId ? 'You' : task.assignee;
  return (
    <Card mode="outlined" onPress={onPress} accessibilityLabel={`${task.name ?? 'Task'}, ${assignee}`}>
      <Card.Content style={styles.cardContent}>
        <View style={styles.cardTop}>
          <Text variant="titleMedium" style={styles.cardTitle}>
            {task.name ?? 'Untitled task'}
          </Text>
          <Chip
            compact
            mode={task.assignee ? 'flat' : 'outlined'}
            style={task.assignee === currentUserId ? { backgroundColor: theme.colors.primaryContainer } : undefined}
          >
            {assignee}
          </Chip>
        </View>
        <Text variant="bodySmall" style={styles.muted}>
          Created {formatRelative(task.created)}
        </Text>
        {task.due ? (
          <Text variant="bodySmall" style={{ color: overdue ? theme.colors.error : theme.colors.onSurfaceVariant }}>
            {overdue ? 'Overdue · ' : 'Due '}
            {formatDate(task.due)}
          </Text>
        ) : null}
      </Card.Content>
    </Card>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  filters: { padding: 12, gap: 10 },
  search: { elevation: 0 },
  searchInput: { minHeight: 0 },
  error: { marginHorizontal: 12 },
  list: { padding: 12, gap: 10, flexGrow: 1 },
  count: { opacity: 0.7, marginBottom: 2 },
  cardContent: { gap: 4 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 },
  cardTitle: { flex: 1, fontWeight: '600' },
  muted: { opacity: 0.7 },
  loading: { marginTop: 32 },
  empty: { alignItems: 'center', marginTop: 48, gap: 6, paddingHorizontal: 24 },
  menuHeader: { paddingHorizontal: 16, paddingVertical: 8, maxWidth: 260 },
});
