import { describe, expect, it } from 'vitest';
import { receiptDirectionLabel, transactionTypeLabel } from './transactions.js';

describe('transaction labels', () => {
  it('distinguishes welcome credit from deposits and transfers', () => {
    expect(transactionTypeLabel({ type: 'DEMO_CREDIT' })).toBe('Welcome demo credit');
    expect(transactionTypeLabel({ type: 'DEPOSIT' })).toBe('Sandbox deposit');
    expect(transactionTypeLabel({ type: 'TRANSFER' })).toBe('Wallet transfer');
  });
  it('shows a completed deposit as completed rather than requested', () => {
    expect(receiptDirectionLabel({ type: 'DEPOSIT', status: 'COMPLETED' })).toBe(
      'Deposit completed',
    );
    expect(receiptDirectionLabel({ type: 'DEPOSIT', status: 'PENDING' })).toBe('Deposit requested');
  });
  it('shows the welcome credit explicitly on the receipt', () => {
    expect(receiptDirectionLabel({ type: 'DEMO_CREDIT', direction: 'CREDIT' })).toBe(
      'Welcome demo credit',
    );
  });
});
