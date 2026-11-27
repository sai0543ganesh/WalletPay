import { Component } from 'react';
import { Link, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext.jsx';
import Layout from './components/Layout.jsx';
import { Button, EmptyState, ErrorNotice, Loading } from './components/UI.jsx';
import AuthPage from './pages/AuthPage.jsx';
import Dashboard from './pages/Dashboard.jsx';
import SendMoney from './pages/SendMoney.jsx';
import AddMoney from './pages/AddMoney.jsx';
import Transactions from './pages/Transactions.jsx';
import Receipt from './pages/Receipt.jsx';
import Analytics from './pages/Analytics.jsx';
import Profile from './pages/Profile.jsx';

function ProtectedRoute() {
  const { user } = useAuth();
  return user ? <Outlet /> : <Navigate to="/login" replace />;
}

class AppErrorBoundary extends Component {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    if (this.state.failed)
      return (
        <div className="app-recovery">
          <h1>Let’s get you back on track.</h1>
          <p>The page could not be displayed. Refresh to try again.</p>
          <Button onClick={() => window.location.reload()}>Refresh WalletPay</Button>
        </div>
      );
    return this.props.children;
  }
}

export default function App() {
  const { loading, error, loadSession } = useAuth();
  if (loading)
    return (
      <div className="app-recovery">
        <Loading label="Opening your wallet…" />
      </div>
    );
  if (error)
    return (
      <div className="app-recovery">
        <h1>WalletPay is temporarily unavailable.</h1>
        <ErrorNotice error={error} onRetry={loadSession} />
      </div>
    );
  return (
    <AppErrorBoundary>
      <Routes>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/register" element={<AuthPage mode="register" />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route index element={<Dashboard />} />
            <Route path="send" element={<SendMoney />} />
            <Route path="add-money" element={<AddMoney />} />
            <Route path="transactions" element={<Transactions />} />
            <Route path="transactions/:id" element={<Receipt />} />
            <Route path="analytics" element={<Analytics />} />
            <Route path="profile" element={<Profile />} />
            <Route
              path="*"
              element={
                <EmptyState
                  title="This page took a wrong turn."
                  action={
                    <Link to="/" className="button button-primary">
                      Back to your wallet
                    </Link>
                  }
                >
                  The address may have changed. Your wallet is still here.
                </EmptyState>
              }
            />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </AppErrorBoundary>
  );
}
