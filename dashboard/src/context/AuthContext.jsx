import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('powertrack_token'));
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('powertrack_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(false);
  }, []);

  const login = async (email, password) => {
    const res = await api.post('/auth/login', { email, password });
    const { token: receivedToken, user: receivedUser } = res.data;
    setToken(receivedToken);
    setUser(receivedUser);
    localStorage.setItem('powertrack_token', receivedToken);
    localStorage.setItem('powertrack_user', JSON.stringify(receivedUser));
    return res.data;
  };

  const register = async (email, password) => {
    const res = await api.post('/auth/register', { email, password });
    const { token: receivedToken, user: receivedUser } = res.data;
    setToken(receivedToken);
    setUser(receivedUser);
    localStorage.setItem('powertrack_token', receivedToken);
    localStorage.setItem('powertrack_user', JSON.stringify(receivedUser));
    return res.data;
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('powertrack_token');
    localStorage.removeItem('powertrack_user');
  };

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        isAuthenticated: !!token,
        loading,
        login,
        register,
        logout
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
