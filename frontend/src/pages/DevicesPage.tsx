import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGetPendingDevices, apiApproveDevice, apiRevokeDevice } from '../lib/api';
import type { StudentDevice } from '../types';
import { StatusBadge } from '../components/StatusBadge';
import { Smartphone, Check, X, ShieldAlert, Key } from 'lucide-react';

export const DevicesPage: React.FC = () => {
  const queryClient = useQueryClient();

  const [revokingDevice, setRevokingDevice] = useState<StudentDevice | null>(null);
  const [revokeReason, setRevokeReason] = useState('');
  const [revokeError, setRevokeError] = useState<string | null>(null);

  const { data: pendingDevices = [], isLoading } = useQuery({
    queryKey: ['pendingDevices'],
    queryFn: apiGetPendingDevices,
  });

  const approveMutation = useMutation({
    mutationFn: (id: number) => apiApproveDevice(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pendingDevices'] });
    },
    onError: (err: any) => {
      alert(err.response?.data?.message || 'Failed to approve device');
    },
  });

  const revokeMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => apiRevokeDevice(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pendingDevices'] });
      setRevokingDevice(null);
      setRevokeReason('');
    },
    onError: (err: any) => {
      setRevokeError(err.response?.data?.message || 'Failed to revoke device');
    },
  });

  const handleRevokeSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!revokingDevice || !revokeReason.trim()) return;
    revokeMutation.mutate({ id: revokingDevice.id, reason: revokeReason });
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Student Device Authorizations</h1>
        <p className="text-sm text-slate-500 mt-1">
          Review and approve hardware-backed cryptographic keys bound to student Android devices.
        </p>
      </div>

      {/* Pending Devices Table */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Smartphone className="w-5 h-5 text-blue-600" />
            <h2 className="text-sm font-bold text-slate-900">Pending Device Approvals ({pendingDevices.length})</h2>
          </div>
        </div>

        {isLoading ? (
          <div className="p-8 text-center text-xs text-slate-400">Loading pending devices...</div>
        ) : pendingDevices.length === 0 ? (
          <div className="p-12 text-center text-slate-400">
            <Smartphone className="w-10 h-10 text-slate-300 mx-auto mb-2" />
            <div className="font-semibold text-slate-700 text-sm">No devices awaiting approval</div>
            <p className="text-xs text-slate-400 mt-0.5">
              All registered student devices have been verified or no pending requests exist.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50/75 border-b border-slate-200 text-slate-500 font-semibold uppercase tracking-wider text-[11px]">
                  <th className="py-3 px-4">Student Roll</th>
                  <th className="py-3 px-4">Student Name</th>
                  <th className="py-3 px-4">Phone Model</th>
                  <th className="py-3 px-4">OS Version</th>
                  <th className="py-3 px-4">NIST P-256 Key Fingerprint</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {pendingDevices.map((d) => (
                  <tr key={d.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="py-3.5 px-4 font-mono font-medium text-slate-900">{d.studentRollNumber}</td>
                    <td className="py-3.5 px-4 font-semibold text-slate-800">{d.studentName}</td>
                    <td className="py-3.5 px-4 text-slate-700">{d.deviceModel}</td>
                    <td className="py-3.5 px-4 text-slate-500">{d.osVersion}</td>
                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-1.5 font-mono text-[11px] text-slate-600 bg-slate-50 px-2 py-1 rounded border border-slate-200 w-fit">
                        <Key className="w-3 h-3 text-slate-400" />
                        <span>{d.publicKeyFingerprint.substring(0, 16)}...</span>
                      </div>
                    </td>
                    <td className="py-3.5 px-4">
                      <StatusBadge status={d.status} />
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => approveMutation.mutate(d.id)}
                          disabled={approveMutation.isPending}
                          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold shadow-xs transition-colors"
                        >
                          <Check className="w-3.5 h-3.5" />
                          Approve
                        </button>
                        <button
                          onClick={() => {
                            setRevokingDevice(d);
                            setRevokeReason('');
                            setRevokeError(null);
                          }}
                          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-semibold transition-colors"
                        >
                          <X className="w-3.5 h-3.5" />
                          Revoke
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Revocation Modal */}
      {revokingDevice && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 border border-slate-100 animate-in fade-in zoom-in-95 duration-150">
            <div className="w-10 h-10 rounded-xl bg-rose-100 text-rose-600 flex items-center justify-center mb-3">
              <ShieldAlert className="w-6 h-6" />
            </div>
            <h3 className="text-base font-bold text-slate-900 mb-1">Revoke Student Device</h3>
            <p className="text-xs text-slate-500 mb-4">
              Revoking student <strong>{revokingDevice.studentName}</strong> ({revokingDevice.studentRollNumber}) will permanently
              invalidate this device key. A reason is required for audit logs.
            </p>

            {revokeError && (
              <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs">
                {revokeError}
              </div>
            )}

            <form onSubmit={handleRevokeSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Mandatory Revocation Reason</label>
                <textarea
                  required
                  rows={3}
                  placeholder="e.g. Lost device reported by student, unauthorized access suspected, or phone replaced."
                  value={revokeReason}
                  onChange={(e) => setRevokeReason(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-slate-200 text-xs focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setRevokingDevice(null)}
                  className="px-3.5 py-1.5 rounded-xl border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={revokeMutation.isPending}
                  className="px-4 py-1.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold shadow-md shadow-rose-500/20 disabled:opacity-50"
                >
                  {revokeMutation.isPending ? 'Revoking...' : 'Confirm Revocation'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
