import { useRef, useState } from 'react';
import { Check, Gift, Landmark, ShieldCheck, WalletCards } from 'lucide-react';
import { Link } from 'react-router-dom';
import { request } from '../api/client.js';
import { useResource } from '../hooks/useResource.js';
import { formatMoney } from '../lib/money.js';
import { Button, ErrorNotice, Field, Loading, Notice, PageHeading } from '../components/UI.jsx';

export default function AddMoney() {
  const funding = useResource('/demo-funds');
  const wallet = useResource('/wallet');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const attemptKey = useRef(null);
  const inFlight = useRef(false);

  async function claimWelcomeCredit() {
    if (inFlight.current || !funding.data?.enabled || !funding.data.welcomeAvailable) return;
    inFlight.current = true;
    setBusy(true);
    setError(null);
    attemptKey.current ||= crypto.randomUUID();
    try {
      const transaction = await request('/demo-funds/welcome', {
        method: 'POST',
        headers: { 'Idempotency-Key': attemptKey.current },
      });
      setResult(transaction);
      funding.reload();
      wallet.reload();
    } catch (failure) {
      setError(failure);
      if (failure.status === 409 || failure.status === 404) funding.reload();
    } finally {
      setBusy(false);
      inFlight.current = false;
    }
  }

  const claimed = funding.data?.enabled && !funding.data.welcomeAvailable;

  return (
    <>
      <PageHeading
        eyebrow="SANDBOX FUNDING"
        title="Add funds"
        description="Start with a one-time ₹100 demo credit, then try wallet transfers."
      />
      <div className="form-layout">
        <div className="funding-content">
          <section className="panel form-panel" aria-labelledby="demo-credit-title">
            <div className="panel-heading">
              <h2 id="demo-credit-title">Your initial demo credit</h2>
              <Gift size={22} aria-hidden="true" />
            </div>
            <ErrorNotice error={error} />
            {result ? (
              <div className="success-state" role="status">
                <span className="success-icon">
                  <Check size={30} aria-hidden="true" />
                </span>
                <h2>Initial demo credit claimed</h2>
                <p>
                  {formatMoney(result.amountMinor)} of simulated money has been added to your
                  wallet. This free credit is available only once per account.
                </p>
                <span className="reference-label">{result.reference}</span>
                <Link to="/send" className="button button-primary">
                  Transfer demo funds
                </Link>
                <Link to={`/transactions/${result.id}`} className="text-link funding-receipt-link">
                  View demo credit receipt
                </Link>
              </div>
            ) : funding.loading ? (
              <Loading label="Checking your demo credit…" />
            ) : funding.error ? (
              <ErrorNotice error={funding.error} onRetry={funding.reload} />
            ) : !funding.data.enabled ? (
              <div className="funding-state">
                <ShieldCheck size={32} aria-hidden="true" />
                <h3>Demo credit unavailable</h3>
                <p>
                  Free demo funding is currently disabled. You can still use any available demo
                  balance for wallet transfers.
                </p>
                <Link className="text-link" to="/">
                  View your wallet
                </Link>
              </div>
            ) : claimed ? (
              <div className="funding-state">
                <Check size={32} aria-hidden="true" />
                <h3>Initial demo credit claimed</h3>
                <p>
                  Your account has already received its one-time ₹100 demo credit. No additional
                  demo top-ups are available.
                </p>
                <Link to="/send" className="button button-primary">
                  Transfer demo funds
                </Link>
                <Link to="/transactions" className="text-link funding-receipt-link">
                  View transactions
                </Link>
              </div>
            ) : (
              <div className="form-stack">
                <div className="welcome-credit-amount">
                  <strong>₹100</strong>
                  <span>Free, once per account</span>
                </div>
                <Notice>
                  <strong>Simulated money, for testing only.</strong> Claim this initial credit to
                  try transfers between WalletPay accounts. It is not a bank payment and cannot be
                  withdrawn.
                </Notice>
                <Button
                  className="button-full"
                  type="button"
                  busy={busy}
                  onClick={claimWelcomeCredit}
                >
                  Claim ₹100 initial demo credit
                </Button>
                <p className="funding-footnote">
                  No card or bank account is needed. Each account receives only one free ₹100
                  credit; there are no further demo top-ups.
                </p>
              </div>
            )}
          </section>
          <section className="panel form-panel" aria-labelledby="bank-link-title">
            <div className="panel-heading bank-heading">
              <h2 id="bank-link-title">Bank account linking</h2>
              <span className="status-badge status-pending">Under development</span>
            </div>
            <p className="bank-development-copy">
              Bank integration and additional funding are still under development. Meanwhile, use
              your initial ₹100 demo credit or available demo balance for wallet transfers.
            </p>
            <fieldset disabled className="bank-fields" aria-labelledby="bank-link-title">
              <Field
                label="Bank name"
                id="bankName"
                placeholder="Not available yet"
                autoComplete="off"
              />
              <Field
                label="Account number"
                id="bankAccountNumber"
                placeholder="Not available yet"
                autoComplete="off"
              />
              <Field
                label="IFSC"
                id="bankIfsc"
                placeholder="Not available yet"
                autoComplete="off"
              />
            </fieldset>
            <p className="funding-footnote">
              These fields are disabled. Do not enter real bank details. No bank connection,
              deposit, charge or withdrawal is performed here.
            </p>
          </section>
        </div>
        <aside className="form-aside" aria-labelledby="available-demo-balance">
          <WalletCards size={31} strokeWidth={1.4} aria-hidden="true" />
          <h2 id="available-demo-balance">Available demo balance</h2>
          {wallet.loading ? (
            <p role="status">Loading your available balance…</p>
          ) : wallet.error ? (
            <ErrorNotice error={wallet.error} onRetry={wallet.reload} />
          ) : (
            <p className="demo-balance-value">{formatMoney(wallet.data.balanceMinor)}</p>
          )}
          <p>
            Use your available balance to send simulated transfers to another registered WalletPay
            user.
          </p>
          <div className="sandbox-funding-note">
            <Landmark size={22} aria-hidden="true" />
            <p>
              WalletPay is a financial application sandbox. Demo money has no cash value, and this
              application does not connect to a bank.
            </p>
          </div>
        </aside>
      </div>
    </>
  );
}
