import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Download, Search, SlidersHorizontal } from 'lucide-react';
import { downloadTransactions, filterQuery } from '../api/client.js';
import { useResource } from '../hooks/useResource.js';
import { Button, EmptyState, ErrorNotice, Loading, PageHeading } from '../components/UI.jsx';
import TransactionList from '../components/TransactionList.jsx';

export default function Transactions() {
  const [filters, setFilters] = useState({
    type: 'ALL',
    direction: 'ALL',
    status: 'ALL',
    search: '',
    from: '',
    to: '',
  });
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  useEffect(() => {
    const timer = setTimeout(() => {
      setFilters((previous) => ({ ...previous, search }));
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);
  const history = useResource(`/transactions${filterQuery({ ...filters, page, size: 10 })}`);
  function change(event) {
    setPage(0);
    setFilters((previous) => ({ ...previous, [event.target.name]: event.target.value }));
  }
  async function exportCsv() {
    setExporting(true);
    setExportError(null);
    try {
      await downloadTransactions(filters);
    } catch (failure) {
      setExportError(failure);
    } finally {
      setExporting(false);
    }
  }
  const data = history.data;
  return (
    <>
      <PageHeading
        eyebrow="YOUR TRANSACTION RECORD"
        title="Transactions"
        description="Search, filter and keep a copy of your wallet activity."
        action={
          <Button variant="outline" busy={exporting} onClick={exportCsv}>
            <Download size={17} />
            Export CSV
          </Button>
        }
      />
      <ErrorNotice error={exportError} />
      <section className="panel">
        <div className="filter-toolbar">
          <div className="filter-search">
            <Search size={18} aria-hidden="true" />
            <label htmlFor="transaction-search" className="sr-only">
              Search transactions
            </label>
            <input
              id="transaction-search"
              placeholder="Search name, phone, note or reference…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              maxLength={80}
            />
          </div>
          <span className="filter-description">
            <SlidersHorizontal size={16} />
            Refine activity
          </span>
        </div>
        <div className="transaction-filters">
          <Select
            label="Transaction type"
            name="type"
            value={filters.type}
            onChange={change}
            options={[
              ['ALL', 'All types'],
              ['TRANSFER', 'Transfers'],
              ['DEMO_CREDIT', 'Welcome demo credit'],
              ['DEPOSIT', 'Deposits'],
            ]}
          />
          <Select
            label="Direction"
            name="direction"
            value={filters.direction}
            onChange={change}
            options={[
              ['ALL', 'All directions'],
              ['CREDIT', 'Money in'],
              ['DEBIT', 'Money out'],
            ]}
          />
          <Select
            label="Status"
            name="status"
            value={filters.status}
            onChange={change}
            options={[
              ['ALL', 'All statuses'],
              ['COMPLETED', 'Completed'],
              ['PENDING', 'Pending'],
            ]}
          />
          <div className="date-filter">
            <label htmlFor="from">From</label>
            <input
              id="from"
              name="from"
              type="date"
              value={filters.from}
              max={filters.to || undefined}
              onChange={change}
            />
          </div>
          <div className="date-filter">
            <label htmlFor="to">To</label>
            <input
              id="to"
              name="to"
              type="date"
              value={filters.to}
              min={filters.from || undefined}
              onChange={change}
            />
          </div>
        </div>
        {history.loading ? (
          <Loading label="Loading transactions…" />
        ) : history.error ? (
          <ErrorNotice error={history.error} onRetry={history.reload} />
        ) : data.items.length ? (
          <TransactionList items={data.items} />
        ) : (
          <EmptyState icon={Search} title="No matching activity">
            Try another search or clear your filters. New transactions appear here as you use your
            wallet.
          </EmptyState>
        )}
        <div className="pagination">
          <span>
            {data
              ? `${data.totalItems} transaction${data.totalItems === 1 ? '' : 's'}`
              : 'Transaction history'}
            {data?.totalItems > 0 && ` · Page ${page + 1} of ${Math.max(1, data.totalPages)}`}
          </span>
          <div>
            <Button
              variant="outline"
              disabled={page === 0 || history.loading}
              onClick={() => setPage((previous) => previous - 1)}
              aria-label="Previous page"
            >
              <ChevronLeft size={17} />
            </Button>
            <Button
              variant="outline"
              disabled={!data || page + 1 >= data.totalPages || history.loading}
              onClick={() => setPage((previous) => previous + 1)}
              aria-label="Next page"
            >
              <ChevronRight size={17} />
            </Button>
          </div>
        </div>
      </section>
      <p className="muted-footnote">
        Times use your browser’s local time zone. Date filters use inclusive UTC dates. CSV exports
        include up to 10,000 matching records.
      </p>
    </>
  );
}

function Select({ label, name, options, ...props }) {
  return (
    <div className="select-filter">
      <label htmlFor={name}>{label}</label>
      <select id={name} name={name} {...props}>
        {options.map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
    </div>
  );
}
