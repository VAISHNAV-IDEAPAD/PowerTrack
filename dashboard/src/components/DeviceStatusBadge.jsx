import React from 'react';
import { Wifi, WifiOff, AlertTriangle, HelpCircle } from 'lucide-react';

export default function DeviceStatusBadge({ status }) {
  switch (status) {
    case 'ONLINE':
      return (
        <span className="badge badge-online" title="Device has made contact within the last 5 minutes.">
          <Wifi size={13} />
          Online
        </span>
      );
    case 'OFFLINE':
      return (
        <span className="badge badge-offline" title="No contact received from device within 5 minutes. Note: does not confirm phone is switched off.">
          <WifiOff size={13} />
          Offline
        </span>
      );
    case 'LOCATION STALE':
      return (
        <span className="badge badge-stale" title="Device contacted recently, but coordinates are older than 15 minutes.">
          <AlertTriangle size={13} />
          Location Stale
        </span>
      );
    case 'UNKNOWN':
    default:
      return (
        <span className="badge badge-unknown" title="Insufficient information to determine status.">
          <HelpCircle size={13} />
          Unknown
        </span>
      );
  }
}
