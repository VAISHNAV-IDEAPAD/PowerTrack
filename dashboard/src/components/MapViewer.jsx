import React, { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Circle, useMap } from 'react-leaflet';
import L from 'leaflet';
import { AlertTriangle, Clock, Battery, Crosshair } from 'lucide-react';

// Fix default Leaflet marker icon issue in Webpack/Vite
const latestIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const staleIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-gold.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const historyIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-grey.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
  iconSize: [18, 30],
  iconAnchor: [9, 30],
  popupAnchor: [1, -25],
  shadowSize: [30, 30]
});

// Helper component to center map on coordinates change
function ChangeMapView({ center, zoom }) {
  const map = useMap();
  useEffect(() => {
    if (center && !isNaN(center[0]) && !isNaN(center[1])) {
      map.setView(center, zoom);
    }
  }, [center, zoom, map]);
  return null;
}

export default function MapViewer({ latestLocation, historyLocations = [], isStale = false }) {
  const [showHistoryMarkers, setShowHistoryMarkers] = useState(true);

  if (!latestLocation) {
    return (
      <div className="card" style={{ textAlign: 'center', padding: '60px 20px' }}>
        <Crosshair size={40} style={{ color: 'var(--text-muted)', marginBottom: '12px' }} />
        <h3 style={{ marginBottom: '8px' }}>No Location Recorded</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
          This device has not yet transmitted any GPS coordinates. Enable tracking on the Android application or tap "Refresh Location" on the device.
        </p>
      </div>
    );
  }

  const centerPos = [latestLocation.latitude, latestLocation.longitude];
  const accuracyRadius = Math.max(5, latestLocation.accuracy_meters || 10);

  // Calculate age of fix
  const recordedDate = new Date(latestLocation.recorded_at_utc);
  const minutesAgo = Math.round((Date.now() - recordedDate.getTime()) / 60000);

  const circleColor = isStale ? '#d97706' : (latestLocation.accuracy_meters <= 20 ? '#16a34a' : '#1d4ed8');

  return (
    <div>
      {/* Explicit Stale Location Warning */}
      {isStale && (
        <div className="alert alert-warning">
          <AlertTriangle size={20} />
          <div>
            <strong>Warning: Location is Stale</strong> — This coordinate was recorded {minutesAgo} minutes ago ({recordedDate.toLocaleString()}).
            The phone is currently offline, powered off, or experiencing GPS interruption. This is the last known location, <em>not</em> real-time coordinates.
          </div>
        </div>
      )}

      {/* Map Controls Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.85rem' }}>
          <span style={{ fontWeight: 600 }}>Active Coordinate:</span>
          <span>{latestLocation.latitude.toFixed(6)}, {latestLocation.longitude.toFixed(6)}</span>
          <span style={{ color: 'var(--text-muted)' }}>(Accuracy: ±{Math.round(latestLocation.accuracy_meters)}m)</span>
        </div>

        <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.85rem', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={showHistoryMarkers}
            onChange={(e) => setShowHistoryMarkers(e.target.checked)}
          />
          Show Historical Fixes ({historyLocations.length})
        </label>
      </div>

      {/* Leaflet Map */}
      <div className="map-container">
        <MapContainer
          center={centerPos}
          zoom={16}
          scrollWheelZoom={true}
          style={{ height: '100%', width: '100%' }}
        >
          <ChangeMapView center={centerPos} zoom={16} />

          {/* OpenStreetMap Tile Layer (Free, no API key needed) */}
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          {/* Accuracy Confidence Circle */}
          <Circle
            center={centerPos}
            radius={accuracyRadius}
            pathOptions={{
              color: circleColor,
              fillColor: circleColor,
              fillOpacity: 0.15,
              weight: 2
            }}
          />

          {/* Latest Location Pin */}
          <Marker
            position={centerPos}
            icon={isStale ? staleIcon : latestIcon}
          >
            <Popup>
              <div style={{ minWidth: '220px' }}>
                <h4 style={{ fontWeight: 700, marginBottom: '6px', color: isStale ? '#b45309' : '#1d4ed8' }}>
                  {isStale ? 'Last Known Location (Stale)' : 'Current Reported Location'}
                </h4>
                <p style={{ margin: '3px 0', fontSize: '0.85rem' }}>
                  <strong>Coordinates:</strong> {latestLocation.latitude.toFixed(6)}, {latestLocation.longitude.toFixed(6)}
                </p>
                <p style={{ margin: '3px 0', fontSize: '0.85rem' }}>
                  <strong>Accuracy:</strong> ±{Math.round(latestLocation.accuracy_meters)} metres
                </p>
                <p style={{ margin: '3px 0', fontSize: '0.85rem' }}>
                  <strong>Battery:</strong> {latestLocation.battery_percentage}%
                </p>
                <p style={{ margin: '3px 0', fontSize: '0.85rem' }}>
                  <strong>Recorded (UTC):</strong> {latestLocation.recorded_at_utc}
                </p>
                <p style={{ margin: '3px 0', fontSize: '0.85rem' }}>
                  <strong>Recorded (Local):</strong> {recordedDate.toLocaleString()}
                </p>
                <p style={{ margin: '3px 0', fontSize: '0.85rem' }}>
                  <strong>Fix Type:</strong> {latestLocation.is_fresh_location ? 'Fresh GPS fix' : 'Cached/Cell fix'}
                </p>
              </div>
            </Popup>
          </Marker>

          {/* Historical Markers */}
          {showHistoryMarkers && historyLocations.map((loc) => {
            if (loc.id === latestLocation.id) return null;
            return (
              <Marker
                key={loc.event_id}
                position={[loc.latitude, loc.longitude]}
                icon={historyIcon}
              >
                <Popup>
                  <div style={{ fontSize: '0.85rem' }}>
                    <h5 style={{ fontWeight: 600, marginBottom: '4px' }}>Historical Fix</h5>
                    <p><strong>Time:</strong> {new Date(loc.recorded_at_utc).toLocaleString()}</p>
                    <p><strong>Accuracy:</strong> ±{Math.round(loc.accuracy_meters)}m</p>
                    <p><strong>Battery:</strong> {loc.battery_percentage}%</p>
                  </div>
                </Popup>
              </Marker>
            );
          })}
        </MapContainer>
      </div>
    </div>
  );
}
