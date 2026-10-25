import React, { useEffect, useState } from 'react';
import api from '../api/client';
import DeviceStatusBadge from '../components/DeviceStatusBadge';
import { Smartphone, Battery, Clock, MapPin, RefreshCw, ChevronRight, Download, Sparkles } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function DashboardHome({ onSelectDevice }) {
  const { user } = useAuth();
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [simulating, setSimulating] = useState(false);

  const fetchDevices = async (isManual = false) => {
    if (isManual) setRefreshing(true);
    try {
      const res = await api.get('/devices');
      setDevices(res.data.devices || []);
      setError('');
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to fetch registered devices.');
    } finally {
      setLoading(false);
      if (isManual) setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchDevices();
    const interval = setInterval(() => fetchDevices(), 15000);
    return () => clearInterval(interval);
  }, []);

  const handleSimulateDevice = async () => {
    setSimulating(true);
    try {
      const devRes = await api.post('/devices/register', {
        device_name: 'Galaxy S24 (Sample Device)',
        platform: 'android'
      });
      const newDevId = devRes.data.device.id;
      // Send initial location fix
      await api.post(`/devices/${newDevId}/locations`, {
        event_id: 'sample-' + Date.now(),
        latitude: 28.6139,
        longitude: 77.2090,
        accuracy_meters: 11.8,
        altitude_meters: 216.5,
        speed_meters_per_second: 1.4,
        bearing_degrees: 180.0,
        battery_percentage: 84,
        recorded_at_utc: new Date().toISOString(),
        location_source: 'gps',
        is_fresh_location: true
      });
      await fetchDevices();
    } catch (e) {
      alert('Error connecting sample device: ' + (e.response?.data?.error || e.message));
    } finally {
      setSimulating(false);
    }
  };

  // Summary counts
  const totalDevices = devices.length;
  const onlineCount = devices.filter((d) => d.status === 'ONLINE').length;
  const staleCount = devices.filter((d) => d.status === 'LOCATION STALE').length;
  const totalLocations = devices.reduce((sum, d) => sum + (d.total_locations || 0), 0);

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text-muted)' }}>
        <RefreshCw size={32} className="spin" style={{ marginBottom: '12px' }} />
        <p>Loading registered devices...</p>
      </div>
    );
  }

  return (
    <div>
      {/* Top Banner & Stats */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 700 }}>Registered Mobile Devices</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
            Live status and geolocation monitoring for authorized devices
          </p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <a
            href="/PowerTrack-v1.0.apk"
            download="PowerTrack-v1.0.apk"
            className="btn btn-outline"
            style={{ fontWeight: 600 }}
          >
            <Download size={16} /> Download APK
          </a>
          <button onClick={() => fetchDevices(true)} className="btn btn-primary" disabled={refreshing}>
            <RefreshCw size={16} className={refreshing ? 'spin' : ''} />
            <span>{refreshing ? 'Updating...' : 'Refresh Devices'}</span>
          </button>
        </div>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {/* Summary KPI Cards */}
      <div className="grid-cols-4" style={{ marginBottom: '24px' }}>
        <div className="card">
          <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '4px' }}>Registered Devices</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700 }}>{totalDevices}</div>
        </div>
        <div className="card">
          <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '4px' }}>Online Devices</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: 'var(--success)' }}>{onlineCount}</div>
        </div>
        <div className="card">
          <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '4px' }}>Location Stale</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: 'var(--warning)' }}>{staleCount}</div>
        </div>
        <div className="card">
          <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '4px' }}>Total Saved Locations</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: 'var(--primary)' }}>{totalLocations}</div>
        </div>
      </div>

      {/* Device List or Empty State */}
      {devices.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '48px 24px', maxWidth: '680px', margin: '0 auto' }}>
          <Smartphone size={48} style={{ color: 'var(--primary)', marginBottom: '16px' }} />
          <h2 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: '8px' }}>Link Your Mobile Device</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginBottom: '24px' }}>
            Install the PowerTrack APK on your phone and sign in with this account (<strong>{user?.email}</strong>) to start tracking.
          </p>

          <div style={{ display: 'flex', justifyContent: 'center', gap: '14px', flexWrap: 'wrap', marginBottom: '28px' }}>
            <a
              href="/PowerTrack-v1.0.apk"
              download="PowerTrack-v1.0.apk"
              className="btn btn-primary"
              style={{ padding: '10px 20px', fontSize: '0.95rem', fontWeight: 600 }}
            >
              <Download size={18} /> Download PowerTrack APK
            </a>

            <button
              onClick={handleSimulateDevice}
              className="btn btn-outline"
              disabled={simulating}
              style={{ padding: '10px 18px', fontSize: '0.95rem' }}
            >
              <Sparkles size={18} color="var(--primary)" /> {simulating ? 'Connecting...' : 'Connect Sample Test Device'}
            </button>
          </div>

          <div style={{ textAlign: 'left', background: 'var(--bg-page)', padding: '18px 22px', borderRadius: '8px', border: '1px solid var(--border)' }}>
            <div style={{ fontWeight: 600, marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-main)' }}>
              Quick 3-Step Setup on Your Android Device:
            </div>
            <ol style={{ paddingLeft: '20px', fontSize: '0.85rem', color: 'var(--text-muted)', lineHeight: '1.8' }}>
              <li>Open Chrome on your phone and download: <code style={{ color: 'var(--primary)', fontWeight: 600 }}>http://192.168.31.19:5000/PowerTrack-v1.0.apk</code></li>
              <li>Install and open PowerTrack, set Server URL to: <code style={{ color: 'var(--primary)', fontWeight: 600 }}>http://192.168.31.19:5000</code></li>
              <li>Sign in with your email <strong>{user?.email}</strong> and tap <strong>Refresh Location Now</strong>!</li>
            </ol>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {devices.map((device) => {
            const latest = device.latest_location;
            const lastContact = device.last_seen_at || latest?.recorded_at_utc;
            const contactDate = lastContact ? new Date(lastContact) : null;
            const battery = device.last_battery_percentage ?? latest?.battery_percentage;

            return (
              <div
                key={device.id}
                className="card"
                style={{
                  cursor: 'pointer',
                  transition: 'transform 0.15s ease, box-shadow 0.15s ease',
                  borderLeft: `4px solid ${
                    device.status === 'ONLINE' ? 'var(--success)' : (device.status === 'LOCATION STALE' ? 'var(--warning)' : 'var(--danger)')
                  }`
                }}
                onClick={() => onSelectDevice(device)}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <div
                      style={{
                        padding: '12px',
                        borderRadius: '50%',
                        background: 'var(--primary-light)',
                        color: 'var(--primary)'
                      }}
                    >
                      <Smartphone size={24} />
                    </div>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>{device.device_name}</h3>
                        <DeviceStatusBadge status={device.status} />
                      </div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                        ID: {device.id} • Platform: {device.platform}
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '24px', flexWrap: 'wrap' }}>
                    {/* Battery */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <Battery size={18} color={battery <= 10 ? 'var(--danger)' : 'var(--text-muted)'} />
                      <span style={{ fontWeight: 600, color: battery <= 10 ? 'var(--danger)' : 'inherit' }}>
                        {battery != null ? `${battery}%` : 'N/A'}
                      </span>
                    </div>

                    {/* Latest Location */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <MapPin size={18} color="var(--primary)" />
                      {latest ? (
                        <span style={{ fontSize: '0.875rem' }}>
                          {latest.latitude.toFixed(4)}, {latest.longitude.toFixed(4)} (±{Math.round(latest.accuracy_meters)}m)
                        </span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>No fixes yet</span>
                      )}
                    </div>

                    {/* Last Contact */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      <Clock size={16} />
                      <span>{contactDate ? contactDate.toLocaleTimeString() : 'Never'}</span>
                    </div>

                    {/* Total records */}
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      <strong>{device.total_locations || 0}</strong> fixes
                    </div>

                    <button className="btn btn-outline" style={{ padding: '6px 12px' }}>
                      Details <ChevronRight size={16} />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
