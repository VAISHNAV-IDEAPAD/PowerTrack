import React, { useState } from 'react';
import { useAuth } from './context/AuthContext';
import Navbar from './components/Navbar';
import AuthPage from './pages/AuthPage';
import DashboardHome from './pages/DashboardHome';
import DeviceDetails from './pages/DeviceDetails';

export default function App() {
  const { isAuthenticated, loading } = useAuth();
  const [selectedDevice, setSelectedDevice] = useState(null);

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <p>Initializing PowerTrack...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <AuthPage />;
  }

  return (
    <div className="app-container">
      <Navbar
        selectedDevice={selectedDevice}
        onBackToDevices={() => setSelectedDevice(null)}
      />

      <main className="main-content">
        {selectedDevice ? (
          <DeviceDetails
            deviceId={selectedDevice.id}
            onBack={() => setSelectedDevice(null)}
          />
        ) : (
          <DashboardHome
            onSelectDevice={(device) => setSelectedDevice(device)}
          />
        )}
      </main>
    </div>
  );
}
