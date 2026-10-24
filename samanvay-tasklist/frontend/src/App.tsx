import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/RequireAuth';
import { AppShell } from './components/AppShell';
import { FormEditorPage } from './pages/admin/FormEditorPage';
import { FormListPage } from './pages/admin/FormListPage';
import { LoginPage } from './pages/LoginPage';
import { TaskDetailPage } from './pages/TaskDetailPage';
import { TaskInboxPage } from './pages/TaskInboxPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<TaskInboxPage />} />
        <Route path="tasks/:id" element={<TaskDetailPage />} />
        <Route
          path="admin/forms"
          element={
            <RequireAuth admin>
              <FormListPage />
            </RequireAuth>
          }
        />
        <Route
          path="admin/forms/:id"
          element={
            <RequireAuth admin>
              <FormEditorPage />
            </RequireAuth>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
