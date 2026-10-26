import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from 'react-native';
import { Button, HelperText, Surface, Text, TextInput, useTheme } from 'react-native-paper';
import { SafeAreaView } from 'react-native-safe-area-context';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export function LoginScreen() {
  const { login, sessionExpired, serverUrl: savedServer, lastUsername } = useAuth();
  const theme = useTheme();
  const [server, setServer] = useState(savedServer);
  const [username, setUsername] = useState(lastUsername);
  const [password, setPassword] = useState('');
  const [showServer, setShowServer] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await login(server, username.trim(), password);
    } catch (e) {
      setError(errorMessage(e));
      setPassword('');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: theme.colors.background }]}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
          <View style={styles.brand}>
            <Surface style={[styles.logo, { backgroundColor: theme.colors.primary }]} elevation={0}>
              <Text style={styles.logoText}>✓</Text>
            </Surface>
            <Text variant="headlineMedium" style={styles.title}>
              Samanvay Tasklist
            </Text>
            <Text variant="bodyMedium" style={styles.subtitle}>
              Sign in to see your tasks
            </Text>
          </View>

          {sessionExpired && !error && (
            <HelperText type="info" visible style={styles.notice}>
              Your session has expired. Please sign in again.
            </HelperText>
          )}

          <TextInput
            mode="outlined"
            label="Username"
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="username"
            textContentType="username"
          />
          <TextInput
            mode="outlined"
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoComplete="password"
            textContentType="password"
            onSubmitEditing={submit}
          />

          {showServer ? (
            <View>
              <TextInput
                mode="outlined"
                label="Server address"
                value={server}
                onChangeText={setServer}
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="url"
              />
              <HelperText type="info" visible>
                The Samanvay Tasklist address, e.g. https://tasks.example.com
              </HelperText>
            </View>
          ) : (
            <Button mode="text" compact onPress={() => setShowServer(true)} style={styles.serverButton}>
              Server: {server}
            </Button>
          )}

          {error && (
            <HelperText type="error" visible style={styles.notice}>
              {error}
            </HelperText>
          )}

          <Button
            mode="contained"
            onPress={submit}
            loading={submitting}
            disabled={submitting || !username.trim() || !password || !server.trim()}
            contentStyle={styles.buttonContent}
          >
            Sign in
          </Button>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { flexGrow: 1, justifyContent: 'center', padding: 24, gap: 14, maxWidth: 480, width: '100%', alignSelf: 'center' },
  brand: { alignItems: 'center', marginBottom: 12, gap: 4 },
  logo: { width: 64, height: 64, borderRadius: 16, alignItems: 'center', justifyContent: 'center', marginBottom: 8 },
  logoText: { color: '#fff', fontSize: 34, fontWeight: '700' },
  title: { fontWeight: '700' },
  subtitle: { opacity: 0.7 },
  notice: { paddingHorizontal: 0 },
  serverButton: { alignSelf: 'flex-start' },
  buttonContent: { paddingVertical: 6 },
});
