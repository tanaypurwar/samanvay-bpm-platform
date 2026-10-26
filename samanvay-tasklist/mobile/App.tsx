import { DarkTheme, DefaultTheme, NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { useColorScheme, View } from 'react-native';
import { ActivityIndicator, PaperProvider } from 'react-native-paper';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ApiError } from './src/api/client';
import { AuthProvider, useAuth } from './src/auth/AuthContext';
import type { RootStackParamList } from './src/navigation';
import { InboxScreen } from './src/screens/InboxScreen';
import { LoginScreen } from './src/screens/LoginScreen';
import { TaskDetailScreen } from './src/screens/TaskDetailScreen';
import { darkTheme, lightTheme } from './src/theme';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      // retry transient failures only; 4xx answers will not change on retry
      retry: (count, error) => count < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
    },
  },
});

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  const scheme = useColorScheme();
  const theme = scheme === 'dark' ? darkTheme : lightTheme;
  const navigationTheme = {
    ...(scheme === 'dark' ? DarkTheme : DefaultTheme),
    colors: {
      ...(scheme === 'dark' ? DarkTheme : DefaultTheme).colors,
      primary: theme.colors.primary,
      background: theme.colors.background,
      card: theme.colors.surface,
    },
  };

  return (
    <SafeAreaProvider>
      <PaperProvider theme={theme}>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <NavigationContainer theme={navigationTheme}>
              <StatusBar style="light" />
              <Screens />
            </NavigationContainer>
          </AuthProvider>
        </QueryClientProvider>
      </PaperProvider>
    </SafeAreaProvider>
  );
}

function Screens() {
  const { user, loading } = useAuth();
  const scheme = useColorScheme();
  const theme = scheme === 'dark' ? darkTheme : lightTheme;

  if (loading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', backgroundColor: theme.colors.background }}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: scheme === 'dark' ? theme.colors.surface : theme.colors.primary },
        headerTintColor: scheme === 'dark' ? theme.colors.onSurface : theme.colors.onPrimary,
        headerTitleStyle: { fontWeight: '700' },
      }}
    >
      {user ? (
        <>
          <Stack.Screen name="Inbox" component={InboxScreen} options={{ title: 'Tasks' }} />
          <Stack.Screen name="Task" component={TaskDetailScreen} options={({ route }) => ({ title: route.params.name ?? 'Task' })} />
        </>
      ) : (
        <Stack.Screen name="Login" component={LoginScreen} options={{ headerShown: false }} />
      )}
    </Stack.Navigator>
  );
}
