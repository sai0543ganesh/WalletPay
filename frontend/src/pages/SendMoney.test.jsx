import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SendMoney from './SendMoney.jsx';
import { request } from '../api/client.js';

vi.mock('../api/client.js', () => ({ request: vi.fn() }));
vi.mock('../hooks/useResource.js', () => ({
  useResource: () => ({ data: { balanceMinor: 50000 }, reload: vi.fn() }),
}));
beforeEach(() => vi.clearAllMocks());

describe('transfer confirmation', () => {
  it('requires review, sends exact paise and preserves idempotency on a retry', async () => {
    const user = userEvent.setup();
    request.mockRejectedValueOnce(new Error('Temporary connection failure')).mockResolvedValueOnce({
      id: 'tx1',
      reference: 'WP-TEST',
      amountMinor: 12529,
      counterpartyPhone: '9876543210',
    });
    render(
      <MemoryRouter>
        <SendMoney />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText('Recipient phone'), '9876543210');
    await user.type(screen.getByLabelText('Amount'), '125.29');
    await user.click(screen.getByRole('button', { name: 'Review transfer' }));
    expect(request).not.toHaveBeenCalled();
    expect(screen.getByRole('heading', { name: 'Review your transfer' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirm transfer' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Temporary connection failure');
    await user.click(screen.getByRole('button', { name: 'Confirm transfer' }));
    expect(await screen.findByRole('heading', { name: 'Transfer complete.' })).toBeInTheDocument();
    const first = request.mock.calls[0][1];
    const second = request.mock.calls[1][1];
    expect(first.body.amountMinor).toBe(12529);
    expect(first.headers['Idempotency-Key']).toBe(second.headers['Idempotency-Key']);
  });
  it('blocks a transfer beyond the displayed available balance', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <SendMoney />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText('Recipient phone'), '9876543210');
    await user.type(screen.getByLabelText('Amount'), '500.01');
    await user.click(screen.getByRole('button', { name: 'Review transfer' }));
    expect(screen.getByRole('alert')).toHaveTextContent('available balance is too low');
    expect(request).not.toHaveBeenCalled();
  });
  it('returns to editable details without submitting', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <SendMoney />
      </MemoryRouter>,
    );
    fireEvent.change(screen.getByLabelText('Recipient phone'), { target: { value: '9876543210' } });
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '1.00' } });
    await user.click(screen.getByRole('button', { name: 'Review transfer' }));
    await user.click(screen.getByRole('button', { name: 'Edit details' }));
    expect(screen.getByLabelText('Amount')).toHaveValue('1.00');
    expect(request).not.toHaveBeenCalled();
  });
});
