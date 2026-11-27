import { AlertCircle, ArrowRight, LoaderCircle, WalletCards } from 'lucide-react';
import { Link } from 'react-router-dom';

export function Brand({ light = false, onClick }) {
  return (
    <Link
      to="/"
      onClick={onClick}
      className={`brand ${light ? 'brand-light' : ''}`}
      aria-label="WalletPay home"
    >
      <span className="brand-mark">
        <WalletCards size={22} strokeWidth={1.8} />
      </span>
      <span>
        Wallet<span className="brand-pay">Pay</span>
        <small>AWS EDITION</small>
      </span>
    </Link>
  );
}

export function Button({ children, busy = false, variant = 'primary', className = '', ...props }) {
  return (
    <button
      {...props}
      className={`button button-${variant} ${className}`}
      disabled={busy || props.disabled}
      aria-busy={busy || undefined}
    >
      {busy && <LoaderCircle className="spin" size={17} aria-hidden="true" />}
      {children}
    </button>
  );
}

export function ErrorNotice({ error, onRetry }) {
  if (!error) return null;
  return (
    <div className="notice notice-error" role="alert">
      <AlertCircle size={20} aria-hidden="true" />
      <div>
        <strong>Something needs your attention</strong>
        <p>{error.message || String(error)}</p>
        {error.requestId && <small>Request: {error.requestId}</small>}
        {onRetry && (
          <button className="text-button" onClick={onRetry}>
            Try again <ArrowRight size={14} />
          </button>
        )}
      </div>
    </div>
  );
}

export function Notice({ children }) {
  return (
    <div className="notice notice-info">
      <AlertCircle size={19} aria-hidden="true" />
      <div>{children}</div>
    </div>
  );
}

export function Field({ label, id, error, hint, ...props }) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...props}
      />
      {hint && <small id={`${id}-hint`}>{hint}</small>}
      {error && (
        <small className="field-error" id={`${id}-error`}>
          {error}
        </small>
      )}
    </div>
  );
}

export function PageHeading({ eyebrow, title, description, action }) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function Loading({ label = 'Loading your wallet…' }) {
  return (
    <div className="loading" role="status">
      <LoaderCircle className="spin" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ icon: Icon = WalletCards, title, children, action }) {
  return (
    <div className="empty-state">
      <span>
        <Icon size={26} strokeWidth={1.5} aria-hidden="true" />
      </span>
      <h3>{title}</h3>
      <p>{children}</p>
      {action}
    </div>
  );
}

export function StatusBadge({ status }) {
  return (
    <span
      className={`status-badge ${status === 'COMPLETED' ? 'status-completed' : 'status-pending'}`}
    >
      {status === 'COMPLETED' ? 'Completed' : status === 'PENDING' ? 'Pending' : status}
    </span>
  );
}
