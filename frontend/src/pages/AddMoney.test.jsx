import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AddMoney from './AddMoney.jsx';
import { request } from '../api/client.js';

const { funding, wallet } = vi.hoisted(() => ({
  funding: { data: null, loading: false, error: null, reload: vi.fn() },
  wallet: { data: { balanceMinor: 3500 }, loading: false, error: null, reload: vi.fn() },
}));
vi.mock('../api/client.js', () => ({ request: vi.fn() }));
vi.mock('../hooks/useResource.js', () => ({
  useResource: (path) => (path === '/demo-funds' ? funding : wallet),
}));

const transaction = {
  id: 'welcome-transaction',
  reference: 'WP-WELCOME',
  amountMinor: 10000,
  type: 'DEMO_CREDIT',
  status: 'COMPLETED',
};
const mount = () =>
  render(
    <MemoryRouter>
      <AddMoney />
    </MemoryRouter>,
  );

beforeEach(() => {
  vi.clearAllMocks();
  funding.data = { enabled: true, welcomeAvailable: true, welcomeCreditMinor: 10000 };
  funding.loading = false;
  funding.error = null;
  wallet.data = { balanceMinor: 3500 };
  wallet.loading = false;
  wallet.error = null;
});

describe('initial one-time demo credit', () => {
  it('claims the fixed credit without an amount or payment form, then removes the claim button', async () => {
    const user = userEvent.setup();
    request.mockResolvedValueOnce(transaction);
    mount();
    expect(screen.queryByLabelText('Amount')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Claim ₹100 initial demo credit' }));
    expect(
      await screen.findByRole('heading', { name: 'Initial demo credit claimed' }),
    ).toBeInTheDocument();
    expect(request).toHaveBeenCalledTimes(1);
    const [path, options] = request.mock.calls[0];
    expect(path).toBe('/demo-funds/welcome');
    expect(options.method).toBe('POST');
    expect(options).not.toHaveProperty('body');
    expect(options.headers['Idempotency-Key']).toMatch(/^[0-9a-f-]{36}$/i);
    expect(
      screen.queryByRole('button', { name: 'Claim ₹100 initial demo credit' }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View demo credit receipt' })).toHaveAttribute(
      'href',
      '/transactions/welcome-transaction',
    );
    expect(screen.getByRole('link', { name: 'Transfer demo funds' })).toHaveAttribute(
      'href',
      '/send',
    );
    expect(funding.reload).toHaveBeenCalledOnce();
    expect(wallet.reload).toHaveBeenCalledOnce();
  });

  it('uses the same idempotency key after an ambiguous connection failure', async () => {
    const user = userEvent.setup();
    request
      .mockRejectedValueOnce(new Error('Connection interrupted'))
      .mockResolvedValueOnce(transaction);
    mount();
    await user.click(screen.getByRole('button', { name: 'Claim ₹100 initial demo credit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Connection interrupted');
    await user.click(screen.getByRole('button', { name: 'Claim ₹100 initial demo credit' }));
    await screen.findByRole('heading', { name: 'Initial demo credit claimed' });
    expect(request.mock.calls[0][1].headers['Idempotency-Key']).toBe(
      request.mock.calls[1][1].headers['Idempotency-Key'],
    );
  });

  it('shows funding unavailable when the feature is disabled', () => {
    funding.data.enabled = false;
    mount();
    expect(screen.getByRole('heading', { name: 'Demo credit unavailable' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Claim/ })).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Create deposit request' }),
    ).not.toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
  });

  it('explains the already-claimed state and shows the available transfer balance', () => {
    funding.data.welcomeAvailable = false;
    mount();
    expect(
      screen.getByRole('heading', { name: 'Initial demo credit claimed' }),
    ).toBeInTheDocument();
    expect(screen.getByText('₹35.00')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Transfer demo funds' })).toHaveAttribute(
      'href',
      '/send',
    );
    expect(screen.queryByRole('button', { name: /Claim/ })).not.toBeInTheDocument();
  });

  it('does not offer a claim while metadata is loading', () => {
    funding.loading = true;
    mount();
    expect(screen.getByRole('status')).toHaveTextContent('Checking your demo credit');
    expect(screen.queryByRole('button', { name: /Claim/ })).not.toBeInTheDocument();
  });

  it('offers a metadata retry after a loading failure', async () => {
    const user = userEvent.setup();
    funding.error = new Error('Cannot reach WalletPay');
    mount();
    expect(screen.getByRole('alert')).toHaveTextContent('Cannot reach WalletPay');
    await user.click(screen.getByRole('button', { name: /Try again/ }));
    expect(funding.reload).toHaveBeenCalledOnce();
    expect(request).not.toHaveBeenCalled();
  });

  it('refreshes eligibility when another session has already claimed the credit', async () => {
    const user = userEvent.setup();
    request.mockRejectedValueOnce(
      Object.assign(new Error('Already claimed'), { status: 409, code: 'WELCOME_ALREADY_CLAIMED' }),
    );
    mount();
    await user.click(screen.getByRole('button', { name: 'Claim ₹100 initial demo credit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Already claimed');
    expect(funding.reload).toHaveBeenCalledOnce();
    expect(
      screen.queryByRole('link', { name: 'View demo credit receipt' }),
    ).not.toBeInTheDocument();
  });

  it('marks bank integration as under development and never accepts bank details', () => {
    mount();
    expect(screen.getByRole('heading', { name: 'Bank account linking' })).toBeInTheDocument();
    expect(screen.getByText('Under development')).toBeInTheDocument();
    expect(screen.getByLabelText('Bank name')).toBeDisabled();
    expect(screen.getByLabelText('Account number')).toBeDisabled();
    expect(screen.getByLabelText('IFSC')).toBeDisabled();
    expect(
      screen.queryByRole('button', { name: /link bank|save bank|connect bank/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/Bank integration and additional funding are still under development/),
    ).toBeInTheDocument();
  });
});
