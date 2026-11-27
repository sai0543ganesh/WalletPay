import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { request } from '../api/client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const loadSession = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setUser(await request('/auth/me'));
    } catch (failure) {
      setUser(null);
      if (failure.status !== 401) setError(failure);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    loadSession();
  }, [loadSession]);
  useEffect(() => {
    const expire = () => setUser(null);
    window.addEventListener('walletpay:session-expired', expire);
    return () => window.removeEventListener('walletpay:session-expired', expire);
  }, []);
  const authenticate = async (mode, values) => {
    const currentUser = await request(`/auth/${mode}`, { method: 'POST', body: values });
    setUser(currentUser);
    return currentUser;
  };
  const logout = async () => {
    await request('/auth/logout', { method: 'POST' });
    setUser(null);
  };
  return (
    <AuthContext.Provider
      value={{ user, setUser, loading, error, loadSession, authenticate, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be inside AuthProvider');
  return context;
}
