import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import AuthPage from './AuthPage.jsx';
import { ThemeProvider } from '../theme/ThemeContext.jsx';

const { authenticate } = vi.hoisted(() => ({ authenticate: vi.fn() }));
vi.mock('../auth/AuthContext.jsx', () => ({ useAuth: () => ({ user: null, authenticate }) }));

describe('registration password validation', () => {
  it('rejects a Unicode password exceeding the server byte limit before making a request', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <ThemeProvider>
          <AuthPage mode="register" />
        </ThemeProvider>
      </MemoryRouter>,
    );
    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Test User' } });
    fireEvent.change(screen.getByLabelText('Phone number'), { target: { value: '9876543210' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: '🔒'.repeat(19) } });
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('72 UTF-8 bytes');
    expect(authenticate).not.toHaveBeenCalled();
  });
});
