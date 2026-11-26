import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAdmin, RequireAuth } from './auth/RequireRole';
import { Layout } from './components/Layout';
import { AccountPage } from './pages/AccountPage';
import { AnalystsPage } from './pages/admin/AnalystsPage';
import { SetupPage } from './pages/admin/SetupPage';
import { AskPage } from './pages/AskPage';
import { HistoryPage } from './pages/HistoryPage';
import { LibraryPage } from './pages/LibraryPage';
import { LoginPage } from './pages/LoginPage';
import { SavedPage } from './pages/SavedPage';
import { UploadPage } from './pages/UploadPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/ask" element={<AskPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/saved" element={<SavedPage />} />
        <Route path="/library" element={<LibraryPage />} />
        <Route path="/upload" element={<UploadPage />} />
        <Route path="/account" element={<AccountPage />} />
        <Route
          path="/admin/setup"
          element={
            <RequireAdmin>
              <SetupPage />
            </RequireAdmin>
          }
        />
        <Route
          path="/admin/analysts"
          element={
            <RequireAdmin>
              <AnalystsPage />
            </RequireAdmin>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/ask" replace />} />
    </Routes>
  );
}
