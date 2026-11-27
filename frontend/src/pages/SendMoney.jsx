import { useState } from 'react';
import { ArrowLeft, ArrowUpRight, Check, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { request } from '../api/client.js';
import { useResource } from '../hooks/useResource.js';
import { formatMoney, parseAmount, phoneIsValid } from '../lib/money.js';
import { Button, ErrorNotice, Field, Notice, PageHeading } from '../components/UI.jsx';

export default function SendMoney() {
  const wallet = useResource('/wallet');
  const [values, setValues] = useState({ recipientPhone: '', amount: '', note: '' });
  const [review, setReview] = useState(null);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const change = (event) =>
    setValues((previous) => ({ ...previous, [event.target.name]: event.target.value }));
  function prepare(event) {
    event.preventDefault();
    setError(null);
    try {
      if (!phoneIsValid(values.recipientPhone))
        throw new Error('Enter a valid recipient phone number.');
      const amountMinor = parseAmount(values.amount);
      if (wallet.data && amountMinor > wallet.data.balanceMinor)
        throw new Error('Your available balance is too low for this transfer.');
      setReview({
        recipientPhone: values.recipientPhone,
        amountMinor,
        note: values.note.trim(),
        key: crypto.randomUUID(),
      });
    } catch (failure) {
      setError(failure);
    }
  }
  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      const { key, ...body } = review;
      const transaction = await request('/transfers', {
        method: 'POST',
        body,
        headers: { 'Idempotency-Key': key },
      });
      setResult(transaction);
      wallet.reload();
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="WALLET TO WALLET"
        title="Send money"
        description="A thoughtful check before every transfer."
      />
      <div className="form-layout">
        <section className="panel form-panel">
          {result ? (
            <div className="success-state">
              <span className="success-icon">
                <Check size={31} />
              </span>
              <h2>Transfer complete.</h2>
              <p>
                You sent <strong>{formatMoney(result.amountMinor)}</strong> to{' '}
                {result.counterpartyName || result.counterpartyPhone || review.recipientPhone}.
              </p>
              <span className="reference-label">{result.reference}</span>
              <Link className="button button-primary" to={`/transactions/${result.id}`}>
                View receipt
                <ArrowUpRight size={17} />
              </Link>
              <button
                className="text-button"
                onClick={() => {
                  setResult(null);
                  setReview(null);
                  setValues({ recipientPhone: '', amount: '', note: '' });
                }}
              >
                Send another transfer
              </button>
            </div>
          ) : (
            <>
              <div className="panel-heading">
                <h2>{review ? 'Review your transfer' : 'Transfer details'}</h2>
                <span className="step-label">{review ? '02 / 02' : '01 / 02'}</span>
              </div>
              <ErrorNotice error={error} />
              {review ? (
                <div className="review-transfer">
                  <p className="review-amount">{formatMoney(review.amountMinor)}</p>
                  <dl className="detail-list">
                    <div>
                      <dt>Recipient phone</dt>
                      <dd>{review.recipientPhone}</dd>
                    </div>
                    <div>
                      <dt>Note</dt>
                      <dd>{review.note || 'No note added'}</dd>
                    </div>
                    <div>
                      <dt>Currency</dt>
                      <dd>INR · sandbox balance</dd>
                    </div>
                  </dl>
                  <Notice>
                    Check the phone number carefully. A completed transfer cannot be edited from
                    this screen.
                  </Notice>
                  <Button className="button-full" busy={busy} onClick={confirm}>
                    Confirm transfer
                    <ArrowUpRight size={18} />
                  </Button>
                  <Button
                    variant="ghost"
                    disabled={busy}
                    onClick={() => {
                      setReview(null);
                      setError(null);
                    }}
                  >
                    <ArrowLeft size={16} />
                    Edit details
                  </Button>
                </div>
              ) : (
                <form className="form-stack" onSubmit={prepare}>
                  <Field
                    label="Recipient phone"
                    id="recipientPhone"
                    name="recipientPhone"
                    type="tel"
                    autoComplete="off"
                    value={values.recipientPhone}
                    onChange={change}
                    required
                    pattern="\+?[0-9]{10,15}"
                    maxLength={16}
                    placeholder="Their WalletPay phone number"
                    hint="Use another registered WalletPay account. Indian mobile numbers work with 10 digits, +91 or 91; other numbers must match the registered format."
                  />
                  <Field
                    label="Amount"
                    id="amount"
                    name="amount"
                    type="text"
                    inputMode="decimal"
                    value={values.amount}
                    onChange={change}
                    required
                    maxLength={14}
                    placeholder="0.00"
                    hint={
                      wallet.data
                        ? `Available: ${formatMoney(wallet.data.balanceMinor)}`
                        : 'Amount in INR. Up to two decimal places.'
                    }
                  />
                  <div className="field">
                    <label htmlFor="note">
                      Note <span className="optional">optional</span>
                    </label>
                    <textarea
                      id="note"
                      name="note"
                      value={values.note}
                      onChange={change}
                      maxLength={160}
                      rows={3}
                      placeholder="What is this for?"
                    />
                    <small>{values.note.length}/160 characters</small>
                  </div>
                  <Button type="submit" className="button-full">
                    Review transfer
                    <ArrowRightIcon />
                  </Button>
                </form>
              )}
            </>
          )}
        </section>
        <aside className="form-aside">
          <ShieldCheck size={31} strokeWidth={1.4} />
          <h2>Clear before you confirm.</h2>
          <p>
            Review the amount and recipient before sending. Your transfer receives a unique
            reference and a receipt.
          </p>
          <ul className="plain-checks">
            <li>
              <Check size={16} />
              Amounts use exact integer paise
            </li>
            <li>
              <Check size={16} />
              Retries use the same request reference
            </li>
            <li>
              <Check size={16} />
              Both wallet updates happen together
            </li>
          </ul>
          <p className="aside-disclaimer">This is a financial sandbox. No real money moves.</p>
        </aside>
      </div>
    </>
  );
}

function ArrowRightIcon() {
  return <ArrowUpRight size={18} aria-hidden="true" />;
}
