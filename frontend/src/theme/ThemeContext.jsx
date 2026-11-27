import { createContext, useContext, useEffect, useState } from 'react';

const STORAGE_KEY = 'walletpay.appearance';
const choices = ['light', 'dark', 'system'];
const ThemeContext = createContext(null);

function savedTheme() {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return choices.includes(value) ? value : 'system';
  } catch {
    return 'system';
  }
}

function systemIsDark() {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
}

export function initializeTheme() {
  const choice = savedTheme();
  document.documentElement.dataset.theme =
    choice === 'system' ? (systemIsDark() ? 'dark' : 'light') : choice;
}

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(savedTheme);
  const [darkSystem, setDarkSystem] = useState(systemIsDark);
  const resolvedTheme = theme === 'system' ? (darkSystem ? 'dark' : 'light') : theme;

  useEffect(() => {
    const query = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (!query) return;
    const changed = (event) => setDarkSystem(event.matches);
    query.addEventListener('change', changed);
    return () => query.removeEventListener('change', changed);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme;
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Appearance still works for this session when browser storage is unavailable.
    }
  }, [theme, resolvedTheme]);

  function chooseTheme(value) {
    if (choices.includes(value)) setTheme(value);
  }

  return (
    <ThemeContext.Provider value={{ theme, resolvedTheme, chooseTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('Theme controls must be inside ThemeProvider');
  return context;
}
