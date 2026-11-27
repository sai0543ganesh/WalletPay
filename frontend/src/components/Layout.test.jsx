import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Layout from './Layout.jsx';
import { Button } from './UI.jsx';
import { ThemeProvider } from '../theme/ThemeContext.jsx';

const { logout } = vi.hoisted(() => ({ logout: vi.fn() }));
vi.mock('../auth/AuthContext.jsx', () => ({
  useAuth: () => ({ user: { fullName: 'Test User', phone: '9876543210' }, logout }),
}));
beforeEach(() => logout.mockReset());

describe('wallet navigation and session controls', () => {
  it('links to the actual send and deposit routes', () => {
    render(
      <MemoryRouter>
        <ThemeProvider>
          <Layout />
        </ThemeProvider>
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Send money' })).toHaveAttribute('href', '/send');
    expect(screen.getByRole('link', { name: 'Add funds' })).toHaveAttribute('href', '/add-money');
    expect(screen.getByRole('link', { name: 'Transactions' })).toHaveAttribute(
      'href',
      '/transactions',
    );
  });
  it('reports a failed logout instead of pretending the session ended', async () => {
    const user = userEvent.setup();
    logout.mockRejectedValueOnce(new Error('Server temporarily unavailable'));
    render(
      <MemoryRouter>
        <ThemeProvider>
          <Layout />
        </ThemeProvider>
      </MemoryRouter>,
    );
    await user.click(screen.getByRole('button', { name: 'Account options' }));
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Server temporarily unavailable');
    expect(screen.getByText('Test User')).toBeInTheDocument();
  });
  it('keeps a busy action disabled even when disabled=false is supplied', () => {
    render(
      <Button busy disabled={false}>
        Send
      </Button>,
    );
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled();
  });
  it('opens account actions from the avatar and supports escape and outside dismissal', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ThemeProvider>
          <Layout />
        </ThemeProvider>
      </MemoryRouter>,
    );
    const trigger = screen.getByRole('button', { name: 'Account options' });
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument();
    await user.click(trigger);
    expect(trigger).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeVisible();
    await user.keyboard('{Escape}');
    expect(trigger).toHaveFocus();
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    await user.click(trigger);
    await user.click(screen.getByText('Personal wallet'));
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    for (const logo of screen.getAllByRole('link', { name: 'WalletPay home' })) {
      expect(logo).toHaveAttribute('href', '/');
    }
  });
});
