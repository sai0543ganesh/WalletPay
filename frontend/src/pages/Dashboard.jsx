import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  CirclePlus,
  ShieldCheck,
  WalletCards,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import { useResource } from '../hooks/useResource.js';
import { formatMoney } from '../lib/money.js';
import { EmptyState, ErrorNotice, Loading, PageHeading } from '../components/UI.jsx';
import TransactionList from '../components/TransactionList.jsx';
import ActivityChart from '../components/ActivityChart.jsx';

export default function Dashboard() {
  const { user } = useAuth();
  const wallet = useResource('/wallet');
  const history = useResource('/transactions?page=0&size=5');
  const analytics = useResource('/analytics?days=30');
  return (
    <>
      <PageHeading
        eyebrow="YOUR WALLET, AT A GLANCE"
        title={`Hello, ${user.fullName.split(' ')[0]}.`}
        description="A clear view of where things stand."
        action={
          <Link className="button button-outline" to="/transactions">
            View activity
            <ArrowUpRight size={17} />
          </Link>
        }
      />
      <div className="dashboard-top">
        <section className="balance-card" aria-label="Wallet balance">
          <div className="balance-card-top">
            <span>
              <WalletCards size={19} />
              Available balance
            </span>
            <span className="currency-tag">INR</span>
          </div>
          {wallet.loading ? (
            <div className="balance-skeleton" aria-label="Loading balance" />
          ) : wallet.error ? (
            <p className="balance-error">
              Balance unavailable. <button onClick={wallet.reload}>Try again</button>
            </p>
          ) : (
            <strong className="balance-amount">{formatMoney(wallet.data.balanceMinor)}</strong>
          )}
          <p className="balance-caption">Your sandbox balance, ready when you are.</p>
          <div className="balance-actions">
            <Link to="/send">
              <ArrowUpRight size={18} />
              Send money
            </Link>
            <Link to="/add-money">
              <CirclePlus size={18} />
              Add funds
            </Link>
          </div>
          <div className="balance-decoration" aria-hidden="true" />
        </section>
        <section className="quick-overview">
          <div className="panel-heading">
            <h2>This month</h2>
            <span>Last 30 days</span>
          </div>
          {analytics.loading ? (
            <Loading label="Loading summary…" />
          ) : analytics.error ? (
            <ErrorNotice error={analytics.error} onRetry={analytics.reload} />
          ) : (
            <div className="summary-pair">
              <div>
                <span className="summary-icon incoming">
                  <ArrowDownLeft size={21} />
                </span>
                <p>Money in</p>
                <strong>{formatMoney(analytics.data.incomeMinor)}</strong>
                <small>Completed credits</small>
              </div>
              <div>
                <span className="summary-icon outgoing">
                  <ArrowUpRight size={21} />
                </span>
                <p>Money out</p>
                <strong>{formatMoney(analytics.data.spendingMinor)}</strong>
                <small>Completed transfers</small>
              </div>
            </div>
          )}
        </section>
      </div>
      <div className="dashboard-bottom">
        <section className="panel activity-panel">
          <div className="panel-heading">
            <div>
              <h2>Recent activity</h2>
              <p>Every movement, in one place.</p>
            </div>
            <Link className="text-link" to="/transactions">
              See all
              <ArrowRight size={15} />
            </Link>
          </div>
          {history.loading ? (
            <Loading label="Loading transactions…" />
          ) : history.error ? (
            <ErrorNotice error={history.error} onRetry={history.reload} />
          ) : history.data.items.length ? (
            <TransactionList items={history.data.items} compact />
          ) : (
            <EmptyState title="A fresh start">
              Your transfers and demo credits will appear here.
            </EmptyState>
          )}
        </section>
        <div className="dashboard-side">
          <section className="panel insights-preview">
            <div className="panel-heading">
              <h2>Your activity</h2>
              <Link to="/analytics" className="icon-button" aria-label="View insights">
                <ArrowUpRight size={19} />
              </Link>
            </div>
            <ActivityChart daily={analytics.data?.daily || []} />
          </section>
          <section className="security-note">
            <ShieldCheck size={26} strokeWidth={1.5} />
            <div>
              <h3>Small details. Strong foundations.</h3>
              <p>
                Authenticated sessions, transfer confirmations and unique transaction references
                help keep your activity clear.
              </p>
            </div>
          </section>
        </div>
      </div>
    </>
  );
}
