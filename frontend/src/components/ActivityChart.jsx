import { formatMoney } from '../lib/money.js';

export default function ActivityChart({ daily = [] }) {
  const recent = daily.slice(-14);
  const maximum = Math.max(1, ...recent.flatMap((day) => [day.incomeMinor, day.spendingMinor]));
  if (!recent.some((day) => day.incomeMinor || day.spendingMinor))
    return (
      <div className="chart-empty">
        <div className="chart-grid" />
        <p>Your activity story starts with your first completed transaction.</p>
      </div>
    );
  return (
    <div className="activity-chart">
      <div className="chart-legend">
        <span>
          <i className="legend-income" />
          Money in
        </span>
        <span>
          <i className="legend-spending" />
          Money out
        </span>
      </div>
      <div
        className="bar-chart"
        role="img"
        aria-label="Daily completed income and spending over the last fourteen available days"
      >
        {recent.map((day) => (
          <div className="bar-group" key={day.date}>
            <div className="bar-pair">
              <span
                className="bar bar-income"
                style={{ height: `${Math.max(2, (day.incomeMinor / maximum) * 100)}%` }}
              />
              <span
                className="bar bar-spending"
                style={{ height: `${Math.max(2, (day.spendingMinor / maximum) * 100)}%` }}
              />
            </div>
            <span>{day.date.slice(-2)}</span>
            <span className="sr-only">
              {day.date}: income {formatMoney(day.incomeMinor)}, spending{' '}
              {formatMoney(day.spendingMinor)}.
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
