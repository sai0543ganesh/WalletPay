import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, parseAmount, phoneIsValid } from './money.js';

describe('exact INR amount parsing', () => {
  it.each([
    ['0.01', 1],
    ['1', 100],
    ['1.1', 110],
    ['125.29', 12529],
    ['0002.05', 205],
    ['1000000.00', 100000000],
  ])('parses %s without floating point rounding', (value, expected) => {
    expect(parseAmount(value)).toBe(expected);
  });
  it.each([
    '0',
    '0.00',
    '-1',
    '+1',
    '1e3',
    'NaN',
    'Infinity',
    '1.001',
    '',
    '1,000',
    '1000000.01',
    '9999999999999999999999999',
  ])('rejects invalid or unsafe amount %s', (value) => {
    expect(() => parseAmount(value)).toThrow();
  });
  it('formats a paise amount into INR', () => expect(formatMoney(12529)).toContain('125.29'));
  it('does not silently format unsafe integers', () =>
    expect(formatMoney(Number.MAX_SAFE_INTEGER + 1)).toBe('Unavailable'));
});

describe('display and recipient validation', () => {
  it('accepts an international phone and rejects non-digits', () => {
    expect(phoneIsValid('+919876543210')).toBe(true);
    expect(phoneIsValid('1234567890')).toBe(true);
    expect(phoneIsValid('123456789')).toBe(false);
    expect(phoneIsValid('a1234567890')).toBe(false);
  });
  it('handles unavailable dates', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate('invalid')).toBe('—');
  });
});
