import { useState } from 'react';
import { ArrowDownLeft, ArrowUpRight, ArrowRightLeft } from 'lucide-react';
import { useResource } from '../hooks/useResource.js';
import { formatMoney } from '../lib/money.js';
import { ErrorNotice, Loading, PageHeading } from '../components/UI.jsx';
import ActivityChart from '../components/ActivityChart.jsx';

export default function Analytics() {
  const [days, setDays] = useState(30);
  const analytics = useResource(`/analytics?days=${days}`);
  return (
    <>
      <PageHeading
        eyebrow="THE BIGGER PICTURE"
        title="Wallet insights"
        description="Understand your completed activity, without the clutter."
        action={
          <div className="period-select">
            <label className="sr-only" htmlFor="period">
              Summary period
            </label>
            <select
              id="period"
              value={days}
              onChange={(event) => setDays(Number(event.target.value))}
            >
              <option value={7}>Last 7 days</option>
              <option value={30}>Last 30 days</option>
              <option value={90}>Last 90 days</option>
            </select>
          </div>
        }
      />
      {analytics.loading ? (
        <Loading label="Loading insights…" />
      ) : analytics.error ? (
        <ErrorNotice error={analytics.error} onRetry={analytics.reload} />
      ) : (
        <>
          <div className="metrics-grid">
            <Metric
              icon={ArrowDownLeft}
              label="Money in"
              value={formatMoney(analytics.data.incomeMinor)}
              color="incoming"
            />
            <Metric
              icon={ArrowUpRight}
              label="Money out"
              value={formatMoney(analytics.data.spendingMinor)}
              color="outgoing"
            />
            <Metric
              icon={ArrowRightLeft}
              label="Wallet transfers"
              value={analytics.data.transferCount}
              color="neutral"
            />
          </div>
          <section className="panel analytics-chart">
            <div className="panel-heading">
              <div>
                <h2>Daily movement</h2>
                <p>Income and spending across the latest 14 available days.</p>
              </div>
              <span>{days}-day summary</span>
            </div>
            <ActivityChart daily={analytics.data.daily} />
          </section>
          <section className="panel insights-explainer">
            <h2>A few things to know</h2>
            <p>
              Only completed transactions count toward these summaries. Your initial demo credit
              counts as money in. Transfers between users are recorded separately for each wallet.
              Daily totals use UTC dates. All amounts are simulated money.
            </p>
          </section>
        </>
      )}
    </>
  );
}

function Metric({ icon: Icon, label, value, color }) {
  return (
    <section className="panel metric-card">
      <span className={`summary-icon ${color}`}>
        <Icon size={21} />
      </span>
      <p>{label}</p>
      <strong>{value}</strong>
      <small>Selected period</small>
    </section>
  );
}
