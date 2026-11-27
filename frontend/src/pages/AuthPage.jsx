import { useState } from 'react';
import { ArrowUpRight, Check, LockKeyhole, ShieldCheck } from 'lucide-react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { Brand, Button, ErrorNotice, Field } from '../components/UI.jsx';
import { validateNewPassword } from '../lib/password.js';
import ThemeSelect from '../components/ThemeSelect.jsx';

export default function AuthPage({ mode = 'login' }) {
  const register = mode === 'register';
  const { user, authenticate } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState({ fullName: '', phone: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  if (user) return <Navigate to="/" replace />;
  const change = (event) =>
    setValues((previous) => ({ ...previous, [event.target.name]: event.target.value }));
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (register) validateNewPassword(values.password);
      await authenticate(
        mode,
        register
          ? { ...values, fullName: values.fullName.trim() }
          : { phone: values.phone, password: values.password },
      );
      navigate('/', { replace: true });
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-page">
      <section className="auth-story">
        <Brand light />
        <div className="auth-story-content">
          <span className="story-kicker">A LITTLE MORE CLARITY.</span>
          <h1>
            Your wallet.
            <br />
            Without the
            <br />
            <em>guesswork.</em>
          </h1>
          <p>Send with confidence. See every movement. Keep your financial picture in focus.</p>
          <div className="story-features">
            <span>
              <Check size={17} />
              Clear transaction records
            </span>
            <span>
              <Check size={17} />
              Thoughtful security
            </span>
            <span>
              <Check size={17} />
              Simple spending insights
            </span>
          </div>
        </div>
        <div className="auth-story-footer">
          <ShieldCheck size={20} />
          <span>
            A financial application sandbox.
            <br />
            No real money or banking connections.
          </span>
        </div>
      </section>
      <main className="auth-form-area">
        <div className="auth-appearance">
          <ThemeSelect />
        </div>
        <div className="auth-mobile-brand">
          <Brand />
        </div>
        <div className="auth-form-wrap">
          <span className="form-symbol">
            <LockKeyhole size={25} strokeWidth={1.6} />
          </span>
          <p className="eyebrow">{register ? 'MAKE ROOM FOR CLARITY' : 'WELCOME BACK'}</p>
          <h2>{register ? 'Create your wallet.' : 'Good to see you.'}</h2>
          <p className="auth-description">
            {register
              ? 'A few details, and your sandbox wallet is ready.'
              : 'Sign in to your personal WalletPay workspace.'}
          </p>
          <ErrorNotice error={error} />
          <form onSubmit={submit} className="form-stack">
            {register && (
              <Field
                label="Full name"
                id="fullName"
                name="fullName"
                value={values.fullName}
                onChange={change}
                required
                minLength={2}
                maxLength={80}
                autoComplete="name"
                placeholder="Your full name"
              />
            )}
            <Field
              label="Phone number"
              id="phone"
              name="phone"
              type="tel"
              value={values.phone}
              onChange={change}
              required
              pattern="\+?[0-9]{10,15}"
              maxLength={16}
              autoComplete="tel"
              placeholder="9876543210"
              hint="10–15 digits, optionally starting with +."
            />
            <Field
              label="Password"
              id="password"
              name="password"
              type="password"
              value={values.password}
              onChange={change}
              required
              minLength={register ? 12 : 1}
              maxLength={register ? 72 : 128}
              autoComplete={register ? 'new-password' : 'current-password'}
              placeholder={register ? 'Create a strong password' : 'Enter your password'}
              hint={
                register
                  ? 'Use at least 12 characters, up to 72 UTF-8 bytes. A unique passphrase works well.'
                  : undefined
              }
            />
            <Button type="submit" busy={busy} className="button-full">
              {register ? 'Create account' : 'Sign in'}
              <ArrowUpRight size={19} />
            </Button>
          </form>
          <p className="auth-switch">
            {register ? 'Already have a wallet?' : 'New to WalletPay?'}{' '}
            <Link to={register ? '/login' : '/register'}>
              {register ? 'Sign in' : 'Create an account'}
            </Link>
          </p>
          <p className="auth-disclosure">
            Sandbox only. Do not enter banking credentials or use real payment details.
          </p>
        </div>
        <span className="auth-copyright">WalletPay AWS Edition · Financial sandbox</span>
      </main>
    </div>
  );
}
