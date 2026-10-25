import React, { useEffect, useState } from 'react';
import api from '../api/client';
import DeviceStatusBadge from '../components/DeviceStatusBadge';
import MapViewer from '../components/MapViewer';
import LocationHistoryTable from '../components/LocationHistoryTable';
import {
  Smartphone,
  Battery,
  MapPin,
  Clock,
  Compass,
  Gauge,
  RefreshCw,
  Settings,
  ArrowLeft
} from 'lucide-react';

export default function DeviceDetails({ deviceId, onBack }) {
  const [device, setDevice] = useState(null);
  const [latestLocation, setLatestLocation] = useState(null);
  const [isStale, setIsStale] = useState(false);
  const [history, setHistory] = useState([]);
  const [pagination, setPagination] = useState({ page: 1, limit: 25, total: 0, totalPages: 1 });
  const [filterDates, setFilterDates] = useState({ startDate: null, endDate: null });
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  // Settings edit modal / form state
  const [isEditingSettings, setIsEditingSettings] = useState(false);
  const [editName, setEditName] = useState('');
  const [editTracking, setEditTracking] = useState(true);
  const [savingSettings, setSavingSettings] = useState(false);

  const fetchDeviceData = async (isManual = false) => {
    if (isManual) setRefreshing(true);
    try {
      // 1. Fetch devices list to get this device info
      const devRes = await api.get('/devices');
      const found = (devRes.data.devices || []).find((d) => d.id === deviceId);
      if (found) {
        setDevice(found);
        setEditName(found.device_name);
        setEditTracking(!!found.tracking_enabled);
      }

      // 2. Fetch latest location
      const locRes = await api.get(`/devices/${deviceId}/latest-location`);
      setLatestLocation(locRes.data.latest_location || null);
      setIsStale(locRes.data.latest_location?.is_stale || false);

      // 3. Fetch location history
      await fetchHistory(pagination.page, filterDates);

      setError('');
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to load device telemetry.');
    } finally {
      setLoading(false);
      if (isManual) setRefreshing(false);
    }
  };

  const fetchHistory = async (page = 1, filters = filterDates) => {
    try {
      let url = `/devices/${deviceId}/location-history?page=${page}&limit=${pagination.limit}`;
      if (filters.startDate) url += `&start_date=${encodeURIComponent(filters.startDate)}`;
      if (filters.endDate) url += `&end_date=${encodeURIComponent(filters.endDate)}`;

      const res = await api.get(url);
      setHistory(res.data.locations || []);
      setPagination(res.data.pagination || { page, limit: 25, total: 0, totalPages: 1 });
    } catch (err) {
      console.error('History fetch error:', err);
    }
  };

  useEffect(() => {
    fetchDeviceData();
    // Poll for fresh location every 10 seconds
    const interval = setInterval(() => fetchDeviceData(), 10000);
    return () => clearInterval(interval);
  }, [deviceId]);

  const handlePageChange = (newPage) => {
    setPagination((prev) => ({ ...prev, page: newPage }));
    fetchHistory(newPage, filterDates);
  };

  const handleFilterChange = (newFilters) => {
    setFilterDates(newFilters);
    setPagination((prev) => ({ ...prev, page: 1 }));
    fetchHistory(1, newFilters);
  };

  const handleSaveSettings = async (e) => {
    e.preventDefault();
    setSavingSettings(true);
    try {
      await api.patch(`/devices/${deviceId}/settings`, {
        device_name: editName,
        tracking_enabled: editTracking
      });
      setIsEditingSettings(false);
      fetchDeviceData(true);
    } catch (err) {
      alert(err.response?.data?.error || 'Failed to update settings.');
    } finally {
      setSavingSettings(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text-muted)' }}>
        <RefreshCw size={32} className="spin" style={{ marginBottom: '12px' }} />
        <p>Loading device telemetry & coordinates...</p>
      </div>
    );
  }

  const battery = latestLocation?.battery_percentage ?? device?.last_battery_percentage;

  return (
    <div>
      {/* Back button & Action Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <button onClick={onBack} className="btn btn-outline" style={{ padding: '8px 12px' }}>
            <ArrowLeft size={16} /> Back to Devices
          </button>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>{device?.device_name || 'Device Details'}</h1>
              {device && <DeviceStatusBadge status={device.status} />}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              ID: {deviceId} • Registered: {new Date(device?.registered_at).toLocaleDateString()}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={() => setIsEditingSettings(!isEditingSettings)} className="btn btn-outline">
            <Settings size={16} /> Device Settings
          </button>
          <button onClick={() => fetchDeviceData(true)} className="btn btn-primary" disabled={refreshing}>
            <RefreshCw size={16} className={refreshing ? 'spin' : ''} />
            <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
        </div>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {/* Settings Panel Drawer / Card */}
      {isEditingSettings && (
        <div className="card" style={{ marginBottom: '20px', background: '#f8fafc' }}>
          <h3 className="card-title" style={{ marginBottom: '12px' }}>Configure Device Settings</h3>
          <form onSubmit={handleSaveSettings}>
            <div className="grid-cols-2">
              <div className="form-group">
                <label className="form-label">Device Display Name</label>
                <input
                  type="text"
                  className="form-input"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  required
                />
              </div>
              <div className="form-group" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                <label className="form-label">Location Tracking Permission</label>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', marginTop: '6px' }}>
                  <input
                    type="checkbox"
                    checked={editTracking}
                    onChange={(e) => setEditTracking(e.target.checked)}
                  />
                  <span>Tracking Enabled</span>
                </label>
              </div>
            </div>
            <div style={{ display: 'flex', gap: '10px', marginTop: '12px' }}>
              <button type="submit" className="btn btn-primary" disabled={savingSettings}>
                {savingSettings ? 'Saving...' : 'Save Settings'}
              </button>
              <button type="button" onClick={() => setIsEditingSettings(false)} className="btn btn-outline">
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Primary Telemetry Metrics Row */}
      <div className="grid-cols-4" style={{ marginBottom: '20px' }}>
        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            <MapPin size={16} color="var(--primary)" />
            <span>Latitude & Longitude</span>
          </div>
          <div style={{ fontSize: '1.25rem', fontWeight: 700, marginTop: '6px', fontFamily: 'monospace' }}>
            {latestLocation ? `${latestLocation.latitude.toFixed(5)}, ${latestLocation.longitude.toFixed(5)}` : 'N/A'}
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
            Accuracy: ±{latestLocation ? Math.round(latestLocation.accuracy_meters) : '—'} metres
          </div>
        </div>

        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            <Battery size={16} color={battery <= 10 ? 'var(--danger)' : 'var(--success)'} />
            <span>Battery Level</span>
          </div>
          <div style={{ fontSize: '1.4rem', fontWeight: 700, marginTop: '6px', color: battery <= 10 ? 'var(--danger)' : 'inherit' }}>
            {battery != null ? `${battery}%` : 'N/A'}
          </div>
          <div style={{ fontSize: '0.8rem', color: battery <= 10 ? 'var(--danger)' : 'var(--text-muted)', marginTop: '2px' }}>
            {battery <= 10 ? 'EMERGENCY 10% THRESHOLD' : 'Normal operating level'}
          </div>
        </div>

        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            <Clock size={16} color="var(--primary)" />
            <span>Recorded Timestamp</span>
          </div>
          <div style={{ fontSize: '1.1rem', fontWeight: 600, marginTop: '6px' }}>
            {latestLocation ? new Date(latestLocation.recorded_at_utc).toLocaleTimeString() : 'Never'}
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
            {latestLocation ? new Date(latestLocation.recorded_at_utc).toLocaleDateString() : 'No records'}
          </div>
        </div>

        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            <Gauge size={16} color="var(--primary)" />
            <span>Speed & Altitude</span>
          </div>
          <div style={{ fontSize: '1.1rem', fontWeight: 600, marginTop: '6px' }}>
            {latestLocation?.speed_meters_per_second != null
              ? `${(latestLocation.speed_meters_per_second * 3.6).toFixed(1)} km/h`
              : 'Stationary'}
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
            Alt: {latestLocation?.altitude_meters != null ? `${latestLocation.altitude_meters.toFixed(1)}m` : '—'}
          </div>
        </div>
      </div>

      {/* Interactive Map */}
      <div style={{ marginBottom: '24px' }}>
        <MapViewer
          latestLocation={latestLocation}
          historyLocations={history}
          isStale={isStale}
        />
      </div>

      {/* Location History Table */}
      <LocationHistoryTable
        locations={history}
        pagination={pagination}
        onPageChange={handlePageChange}
        onFilterChange={handleFilterChange}
      />
    </div>
  );
}
