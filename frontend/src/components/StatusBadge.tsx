import React from 'react';
import type { DriveStatus } from '../types';

interface StatusBadgeProps {
  status: DriveStatus | 'PRESENT' | 'ABSENT' | 'VERIFIED' | 'MANUAL_OVERRIDE' | 'NOT_ATTENDED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REVOKED';
  className?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, className = '' }) => {
  switch (status) {
    case 'OPEN':
      return (
        <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200 ${className}`}>
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
          OPEN
        </span>
      );
    case 'SCHEDULED':
      return (
        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800 border border-blue-200 ${className}`}>
          SCHEDULED
        </span>
      );
    case 'DRAFT':
      return (
        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200 ${className}`}>
          DRAFT
        </span>
      );
    case 'CLOSED':
      return (
        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200 ${className}`}>
          CLOSED
        </span>
      );
    case 'FINALIZED':
      return (
        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-purple-100 text-purple-800 border border-purple-200 ${className}`}>
          FINALIZED
        </span>
      );
    case 'PRESENT':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-300 ${className}`}>
          PRESENT
        </span>
      );
    case 'ABSENT':
      return (
        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-rose-50 text-rose-700 border border-rose-300 ${className}`}>
          ABSENT
        </span>
      );
    case 'VERIFIED':
      return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200 ${className}`}>
          VERIFIED
        </span>
      );
    case 'MANUAL_OVERRIDE':
      return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-50 text-amber-700 border border-amber-200 ${className}`}>
          OVERRIDE
        </span>
      );
    case 'APPROVED':
      return (
        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 ${className}`}>
          APPROVED
        </span>
      );
    case 'PENDING_APPROVAL':
      return (
        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 ${className}`}>
          PENDING
        </span>
      );
    case 'REVOKED':
      return (
        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800 ${className}`}>
          REVOKED
        </span>
      );
    default:
      return (
        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600 ${className}`}>
          {status}
        </span>
      );
  }
};
