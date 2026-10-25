import React, { useState } from 'react';
import { Calendar, ChevronLeft, ChevronRight, Filter } from 'lucide-react';

export default function LocationHistoryTable({
  locations = [],
  pagination = { page: 1, limit: 50, total: 0, totalPages: 1 },
  onPageChange,
  onFilterChange
}) {
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const handleApplyFilter = (e) => {
    e.preventDefault();
    onFilterChange?.({
      startDate: startDate ? new Date(startDate).toISOString() : null,
      endDate: endDate ? new Date(endDate).toISOString() : null
    });
  };

  const handleClearFilter = () => {
    setStartDate('');
    setEndDate('');
    onFilterChange?.({ startDate: null, endDate: null });
  };

  return (
    <div className="card">
      <div className="card-header" style={{ flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h3 className="card-title">Location Records History</h3>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Showing {locations.length} of {pagination.total} records
          </span>
        </div>

        {/* Date Filter Bar */}
        <form onSubmit={handleApplyFilter} style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>From:</span>
            <input
              type="date"
              className="form-input"
              style={{ width: 'auto', padding: '4px 8px', fontSize: '0.8rem' }}
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>To:</span>
            <input
              type="date"
              className="form-input"
              style={{ width: 'auto', padding: '4px 8px', fontSize: '0.8rem' }}
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>
          <button type="submit" className="btn btn-primary" style={{ padding: '6px 12px', fontSize: '0.8rem' }}>
            <Filter size={14} /> Filter
          </button>
          {(startDate || endDate) && (
            <button type="button" onClick={handleClearFilter} className="btn btn-outline" style={{ padding: '6px 12px', fontSize: '0.8rem' }}>
              Reset
            </button>
          )}
        </form>
      </div>

      {locations.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '30px 10px', color: 'var(--text-muted)' }}>
          No location history records matching current criteria.
        </div>
      ) : (
        <div className="table-responsive">
          <table className="table">
            <thead>
              <tr>
                <th>Date & Time (Local)</th>
                <th>Latitude, Longitude</th>
                <th>Accuracy</th>
                <th>Battery</th>
                <th>Speed & Heading</th>
                <th>Source</th>
              </tr>
            </thead>
            <tbody>
              {locations.map((loc) => {
                const date = new Date(loc.recorded_at_utc);
                return (
                  <tr key={loc.event_id}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{date.toLocaleDateString()}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        {date.toLocaleTimeString()} (UTC: {loc.recorded_at_utc.substring(11, 19)})
                      </div>
                    </td>
                    <td>
                      <span style={{ fontFamily: 'monospace' }}>
                        {loc.latitude.toFixed(6)}, {loc.longitude.toFixed(6)}
                      </span>
                    </td>
                    <td>
                      <span
                        className="badge"
                        style={{
                          background: loc.accuracy_meters <= 20 ? 'var(--success-bg)' : (loc.accuracy_meters <= 50 ? 'var(--warning-bg)' : 'var(--danger-bg)'),
                          color: loc.accuracy_meters <= 20 ? 'var(--success)' : (loc.accuracy_meters <= 50 ? 'var(--warning)' : 'var(--danger)')
                        }}
                      >
                        ±{Math.round(loc.accuracy_meters)}m
                      </span>
                    </td>
                    <td>
                      <span style={{ fontWeight: 600, color: loc.battery_percentage <= 10 ? 'var(--danger)' : 'inherit' }}>
                        {loc.battery_percentage}%
                      </span>
                    </td>
                    <td>
                      {loc.speed_meters_per_second != null ? (
                        <span>{(loc.speed_meters_per_second * 3.6).toFixed(1)} km/h</span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)' }}>—</span>
                      )}
                      {loc.bearing_degrees != null && (
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.8rem', marginLeft: '6px' }}>
                          ({Math.round(loc.bearing_degrees)}°)
                        </span>
                      )}
                    </td>
                    <td>
                      <span style={{ textTransform: 'uppercase', fontSize: '0.8rem', fontWeight: 500 }}>
                        {loc.location_source}
                      </span>
                      <span style={{ fontSize: '0.75rem', color: loc.is_fresh_location ? 'var(--success)' : 'var(--warning)', marginLeft: '4px' }}>
                        ({loc.is_fresh_location ? 'fresh' : 'cached'})
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination Footer */}
      {pagination.totalPages > 1 && (
        <div className="pagination">
          <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Page {pagination.page} of {pagination.totalPages}
          </span>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              className="btn btn-outline"
              disabled={pagination.page <= 1}
              onClick={() => onPageChange?.(pagination.page - 1)}
            >
              <ChevronLeft size={16} /> Prev
            </button>
            <button
              className="btn btn-outline"
              disabled={pagination.page >= pagination.totalPages}
              onClick={() => onPageChange?.(pagination.page + 1)}
            >
              Next <ChevronRight size={16} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
