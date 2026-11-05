import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { LoginPage } from './pages/LoginPage';
import { DrivesPage } from './pages/DrivesPage';
import { DriveDetailPage } from './pages/DriveDetailPage';
import { DevicesPage } from './pages/DevicesPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

const ProtectedOfficerRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, isLoading, isOfficer } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 text-slate-500 text-xs">
        <div className="w-6 h-6 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mr-2" />
        Authenticating session...
      </div>
    );
  }

  if (!user || !isOfficer) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar />
      <main className="flex-1">{children}</main>
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/drives"
              element={
                <ProtectedOfficerRoute>
                  <DrivesPage />
                </ProtectedOfficerRoute>
              }
            />
            <Route
              path="/drives/:id"
              element={
                <ProtectedOfficerRoute>
                  <DriveDetailPage />
                </ProtectedOfficerRoute>
              }
            />
            <Route
              path="/devices"
              element={
                <ProtectedOfficerRoute>
                  <DevicesPage />
                </ProtectedOfficerRoute>
              }
            />
            <Route path="*" element={<Navigate to="/drives" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;
