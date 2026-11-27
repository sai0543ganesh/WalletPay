import { Monitor, Moon, Sun } from 'lucide-react';
import { useTheme } from '../theme/ThemeContext.jsx';

export default function ThemeSelect() {
  const { theme, chooseTheme } = useTheme();
  const Icon = theme === 'system' ? Monitor : theme === 'dark' ? Moon : Sun;
  return (
    <label className="theme-select">
      <Icon size={16} aria-hidden="true" />
      <span className="sr-only">Appearance</span>
      <select value={theme} onChange={(event) => chooseTheme(event.target.value)}>
        <option value="light">Light</option>
        <option value="dark">Dark</option>
        <option value="system">System</option>
      </select>
    </label>
  );
}
