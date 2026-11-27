export const MAX_AMOUNT_MINOR = 100000000;

export function parseAmount(value) {
  const input = String(value).trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(input))
    throw new Error('Enter an amount with no more than two decimal places.');
  const [whole, fraction = ''] = input.split('.');
  const minor = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  if (minor < 1n) throw new Error('Enter an amount greater than zero.');
  if (minor > BigInt(MAX_AMOUNT_MINOR))
    throw new Error('The maximum amount is ₹10,00,000 per transaction.');
  return Number(minor);
}

export function formatMoney(minor = 0) {
  if (!Number.isSafeInteger(minor)) return 'Unavailable';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
  }).format(minor / 100);
}

export function formatDate(value, options = {}) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    ...options,
  }).format(date);
}

export function phoneIsValid(phone) {
  return /^\+?[0-9]{10,15}$/.test(phone);
}
