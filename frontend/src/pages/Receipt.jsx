import { ArrowLeft, Printer, ShieldCheck } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { useResource } from '../hooks/useResource.js';
import { formatDate, formatMoney } from '../lib/money.js';
import { Brand, Button, ErrorNotice, Loading, StatusBadge } from '../components/UI.jsx';
import { receiptDirectionLabel, transactionTypeLabel } from '../lib/transactions.js';

export default function Receipt() {
  const { id } = useParams();
  const receipt = useResource(`/transactions/${encodeURIComponent(id)}/receipt`);
  if (receipt.loading) return <Loading label="Loading your receipt…" />;
  if (receipt.error) return <ErrorNotice error={receipt.error} onRetry={receipt.reload} />;
  const transaction = receipt.data;
  return (
    <>
      <div className="receipt-toolbar">
        <Link className="text-link" to="/transactions">
          <ArrowLeft size={17} />
          Back to transactions
        </Link>
        <Button variant="outline" onClick={() => window.print()}>
          <Printer size={17} />
          Print receipt
        </Button>
      </div>
      <article className="panel receipt">
        <div className="receipt-brand">
          <Brand />
          <span>TRANSACTION RECEIPT</span>
        </div>
        <div className="receipt-title">
          <span
            className={`receipt-direction ${transaction.direction === 'CREDIT' ? 'positive' : ''}`}
          >
            {receiptDirectionLabel(transaction)}
          </span>
          <h1>{formatMoney(transaction.amountMinor)}</h1>
          <StatusBadge status={transaction.status} />
        </div>
        <dl className="detail-list">
          <div>
            <dt>Reference</dt>
            <dd className="mono">{transaction.reference}</dd>
          </div>
          <div>
            <dt>Transaction ID</dt>
            <dd className="mono">{transaction.id}</dd>
          </div>
          <div>
            <dt>Type</dt>
            <dd>{transactionTypeLabel(transaction)}</dd>
          </div>
          <div>
            <dt>Direction</dt>
            <dd>{transaction.direction === 'DEBIT' ? 'Money out' : 'Money in'}</dd>
          </div>
          {transaction.counterpartyPhone && (
            <div>
              <dt>Other wallet</dt>
              <dd>
                {transaction.counterpartyName}
                <small>{transaction.counterpartyPhone}</small>
              </dd>
            </div>
          )}
          <div>
            <dt>Date and time</dt>
            <dd>
              {formatDate(transaction.createdAt, {
                hour: '2-digit',
                minute: '2-digit',
                timeZoneName: 'short',
              })}
            </dd>
          </div>
          <div>
            <dt>Currency</dt>
            <dd>INR</dd>
          </div>
          {transaction.note && (
            <div>
              <dt>Note</dt>
              <dd>{transaction.note}</dd>
            </div>
          )}
        </dl>
        <div className="receipt-disclaimer">
          <ShieldCheck size={22} />
          <p>
            This receipt records activity in a financial application sandbox. It is not evidence of
            a bank payment or a real-money transfer.
            {transaction.status === 'PENDING' &&
              ' This pending request has not credited your balance.'}
          </p>
        </div>
      </article>
    </>
  );
}
