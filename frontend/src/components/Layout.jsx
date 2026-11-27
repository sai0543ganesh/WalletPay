import {
  BarChart3,
  ArrowUpRight,
  CirclePlus,
  History,
  LayoutDashboard,
  LogOut,
  Menu,
  ShieldCheck,
  UserRound,
  X,
  ChevronsUpDown,
} from 'lucide-react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/AuthContext.jsx';
import { Brand, Button, ErrorNotice } from './UI.jsx';
import ThemeSelect from './ThemeSelect.jsx';

const navigation = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/send', label: 'Send money', icon: ArrowUpRight },
  { to: '/add-money', label: 'Add funds', icon: CirclePlus },
  { to: '/transactions', label: 'Transactions', icon: History },
  { to: '/analytics', label: 'Insights', icon: BarChart3 },
  { to: '/profile', label: 'Your profile', icon: UserRound },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [accountOpen, setAccountOpen] = useState(false);
  const account = useRef(null);
  const accountButton = useRef(null);
  useEffect(() => {
    if (!accountOpen) return;
    function outside(event) {
      if (!account.current?.contains(event.target)) setAccountOpen(false);
    }
    function escape(event) {
      if (event.key === 'Escape') {
        setAccountOpen(false);
        accountButton.current?.focus();
      }
    }
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
    };
  }, [accountOpen]);
  function closeNavigation() {
    setOpen(false);
    setAccountOpen(false);
  }
  async function signOut() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await logout();
      closeNavigation();
      navigate('/login');
    } catch (failure) {
      closeNavigation();
      setError(failure);
    } finally {
      setBusy(false);
    }
  }
  const initials = (user?.fullName || 'Wallet User')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>
      <header className="mobile-header">
        <Brand onClick={closeNavigation} />
        <button
          className="icon-button"
          aria-label={open ? 'Close navigation' : 'Open navigation'}
          aria-expanded={open}
          aria-controls="main-navigation"
          onClick={() => {
            setOpen(!open);
            setAccountOpen(false);
          }}
        >
          {open ? <X /> : <Menu />}
        </button>
      </header>
      {open && (
        <button className="nav-backdrop" aria-label="Close navigation" onClick={closeNavigation} />
      )}
      <aside className={`sidebar ${open ? 'sidebar-open' : ''}`} id="main-navigation">
        <div className="desktop-brand">
          <Brand onClick={closeNavigation} />
        </div>
        <p className="navigation-label">YOUR WORKSPACE</p>
        <nav aria-label="Main navigation">
          {navigation.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              onClick={closeNavigation}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              <Icon size={20} strokeWidth={1.7} aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sandbox-card">
            <ShieldCheck size={23} aria-hidden="true" />
            <strong>A safe place to explore.</strong>
            <p>
              Sandbox balances only.
              <br />
              No real money moves here.
            </p>
          </div>
          <div className="account-control" ref={account}>
            {accountOpen && (
              <div className="account-popover" id="account-options">
                <Link className="account-action" to="/profile" onClick={closeNavigation}>
                  <UserRound size={17} aria-hidden="true" /> Your profile
                </Link>
                <Button variant="ghost" busy={busy} onClick={signOut}>
                  <LogOut size={17} aria-hidden="true" /> Sign out
                </Button>
              </div>
            )}
            <button
              type="button"
              className="sidebar-user"
              ref={accountButton}
              aria-label="Account options"
              aria-expanded={accountOpen}
              aria-controls={accountOpen ? 'account-options' : undefined}
              onClick={() => setAccountOpen(!accountOpen)}
            >
              <span className="avatar">{initials}</span>
              <span className="sidebar-user-details">
                <strong>{user?.fullName}</strong>
                <span>{user?.phone}</span>
              </span>
              <ChevronsUpDown size={16} aria-hidden="true" />
            </button>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span className="topbar-label">Personal wallet</span>
          <div className="topbar-controls">
            <ThemeSelect />
            <span className="sandbox-pill">
              <span />
              Sandbox environment
            </span>
          </div>
        </header>
        <main id="main-content" tabIndex={-1}>
          {error && <ErrorNotice error={error} />}
          <Outlet />
        </main>
        <footer className="workspace-footer">
          <span>WalletPay AWS Edition</span>
          <span>Built for clarity. Designed as a sandbox.</span>
        </footer>
      </div>
    </div>
  );
}
