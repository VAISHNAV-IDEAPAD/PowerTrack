import React from 'react';
import { Navigation, LogOut, Smartphone } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function Navbar({ selectedDevice, onBackToDevices }) {
  const { user, logout } = useAuth();

  return (
    <header className="navbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <a href="#" onClick={(e) => { e.preventDefault(); onBackToDevices?.(); }} className="nav-brand">
          <Navigation size={24} />
          <span>PowerTrack</span>
        </a>

        {selectedDevice && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem', color: 'var(--text-muted)' }}>
            <span>/</span>
            <button
              onClick={onBackToDevices}
              className="btn btn-outline"
              style={{ padding: '4px 8px', fontSize: '0.8rem' }}
            >
              Devices
            </button>
            <span>/</span>
            <span style={{ fontWeight: 600, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Smartphone size={14} />
              {selectedDevice.device_name}
            </span>
          </div>
        )}
      </div>

      <div className="nav-actions">
        <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
          {user?.email}
        </span>
        <button onClick={logout} className="btn btn-outline" title="Log Out">
          <LogOut size={16} />
          <span>Log Out</span>
        </button>
      </div>
    </header>
  );
}
