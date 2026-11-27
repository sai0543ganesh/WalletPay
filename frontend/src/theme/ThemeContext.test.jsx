import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { initializeTheme, ThemeProvider } from './ThemeContext.jsx';
import ThemeSelect from '../components/ThemeSelect.jsx';

let query;
let listeners;
beforeEach(() => {
  localStorage.clear();
  listeners = new Set();
  query = {
    matches: false,
    addEventListener: (_, fn) => listeners.add(fn),
    removeEventListener: (_, fn) => listeners.delete(fn),
  };
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => query),
  );
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  document.documentElement.removeAttribute('data-theme');
});

describe('site appearance', () => {
  it('persists a selection and restores it before rendering the next visit', async () => {
    const user = userEvent.setup();
    const view = render(
      <ThemeProvider>
        <ThemeSelect />
      </ThemeProvider>,
    );
    await user.selectOptions(screen.getByRole('combobox', { name: 'Appearance' }), 'dark');
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    expect(localStorage.getItem('walletpay.appearance')).toBe('dark');
    view.unmount();
    document.documentElement.removeAttribute('data-theme');
    initializeTheme();
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    render(
      <ThemeProvider>
        <ThemeSelect />
      </ThemeProvider>,
    );
    expect(screen.getByRole('combobox', { name: 'Appearance' })).toHaveValue('dark');
  });
  it('follows system changes only while system is selected and cleans up listeners', async () => {
    const user = userEvent.setup();
    const view = render(
      <ThemeProvider>
        <ThemeSelect />
      </ThemeProvider>,
    );
    act(() => listeners.forEach((fn) => fn({ matches: true })));
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Appearance' }), 'light');
    act(() => listeners.forEach((fn) => fn({ matches: true })));
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
    view.unmount();
    expect(listeners.size).toBe(0);
  });
  it('still changes appearance when browser storage is blocked', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('Blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('Blocked');
    });
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <ThemeSelect />
      </ThemeProvider>,
    );
    await user.selectOptions(screen.getByRole('combobox', { name: 'Appearance' }), 'dark');
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
  });
});
