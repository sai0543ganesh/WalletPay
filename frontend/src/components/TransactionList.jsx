import { ArrowDownLeft, ArrowUpRight, ChevronRight, CirclePlus, Gift } from 'lucide-react';
import { Link } from 'react-router-dom';
import { formatDate, formatMoney } from '../lib/money.js';
import { EmptyState, StatusBadge } from './UI.jsx';
import { transactionTypeLabel } from '../lib/transactions.js';

export default function TransactionList({ items = [], compact = false }) {
  if (!items.length)
    return (
      <EmptyState title="A fresh start">
        Your transfers and demo credits will appear here.
      </EmptyState>
    );
  return (
    <div className="transaction-list">
      {items.map((item) => {
        const debit = item.direction === 'DEBIT';
        const Icon =
          item.type === 'DEMO_CREDIT'
            ? Gift
            : item.type === 'DEPOSIT'
              ? CirclePlus
              : debit
                ? ArrowUpRight
                : ArrowDownLeft;
        return (
          <Link
            className="transaction-row"
            key={item.id}
            to={`/transactions/${item.id}`}
            aria-label={`View ${item.reference}, ${formatMoney(item.amountMinor)}, ${item.status.toLowerCase()}`}
          >
            <span className={`transaction-icon ${debit ? 'debit' : 'credit'}`}>
              <Icon size={21} aria-hidden="true" />
            </span>
            <div className="transaction-details">
              <strong>
                {item.type === 'TRANSFER'
                  ? item.counterpartyName || item.counterpartyPhone || 'Wallet transfer'
                  : transactionTypeLabel(item)}
              </strong>
              <span>
                {compact
                  ? formatDate(item.createdAt)
                  : `${item.reference} · ${formatDate(item.createdAt)}`}
              </span>
            </div>
            <div className="transaction-amount">
              <strong className={debit ? '' : 'positive'}>
                {debit ? '−' : '+'}
                {formatMoney(item.amountMinor)}
              </strong>
              <StatusBadge status={item.status} />
            </div>
            <ChevronRight size={17} className="row-chevron" aria-hidden="true" />
          </Link>
        );
      })}
    </div>
  );
}
