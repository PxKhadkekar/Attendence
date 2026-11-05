import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  apiGetDriveById,
  apiGetAttendanceSummary,
  apiGetAttendanceRecords,
  apiScheduleDrive,
  apiOpenDrive,
  apiCloseDrive,
  apiFinalizeDrive,
  apiDownloadTemplate,
  apiUploadExcel,
  apiConfirmImport,
  apiDownloadRejectedRows,
  apiExportDriveReport,
  apiRecordCorrection,
} from '../lib/api';
import { StatusBadge } from '../components/StatusBadge';
import type { ImportPreview } from '../types';
import {
  ArrowLeft,
  Calendar,
  MapPin,
  FileSpreadsheet,
  Download,
  Upload,
  Search,
  AlertTriangle,
  Radio,
  UserCheck,
  Percent,
} from 'lucide-react';

export const DriveDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const driveId = Number(id);
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<'attendance' | 'import' | 'export'>('attendance');
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Excel Upload State
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadPreview, setUploadPreview] = useState<ImportPreview | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadLoading, setUploadLoading] = useState(false);

  // Manual Override Correction State
  const [correctionStudent, setCorrectionStudent] = useState<{ id: number; name: string; roll: string; currentStatus: string } | null>(null);
  const [correctionNewStatus, setCorrectionNewStatus] = useState<'PRESENT' | 'ABSENT'>('PRESENT');
  const [correctionReason, setCorrectionReason] = useState('');
  const [correctionError, setCorrectionError] = useState<string | null>(null);

  // Export State
  const [includeAudit, setIncludeAudit] = useState(false);
  const [exportLoading, setExportLoading] = useState(false);

  // SSE Connected indicator
  const [sseConnected, setSseConnected] = useState(false);

  // 1. Fetch Drive Details
  const { data: drive, isLoading: driveLoading } = useQuery({
    queryKey: ['drive', driveId],
    queryFn: () => apiGetDriveById(driveId),
  });

  // 2. Fetch Live Attendance Summary
  const { data: summary, refetch: refetchSummary } = useQuery({
    queryKey: ['attendanceSummary', driveId],
    queryFn: () => apiGetAttendanceSummary(driveId),
    refetchInterval: 5000, // Background polling fallback alongside SSE
  });

  // 3. Fetch Student Attendance Records
  const { data: records = [], refetch: refetchRecords } = useQuery({
    queryKey: ['attendanceRecords', driveId, searchQuery, statusFilter],
    queryFn: () => apiGetAttendanceRecords(driveId, searchQuery, statusFilter),
    refetchInterval: 5000,
  });

  // SSE Real-Time Stream Subscription
  useEffect(() => {
    if (!driveId) return;

    const eventSource = new EventSource(`/api/v1/drives/${driveId}/attendance/stream`);

    eventSource.onopen = () => {
      setSseConnected(true);
    };

    eventSource.addEventListener('CONNECTED', () => {
      setSseConnected(true);
    });

    eventSource.addEventListener('ATTENDANCE_MARKED', () => {
      refetchSummary();
      refetchRecords();
      queryClient.invalidateQueries({ queryKey: ['drive', driveId] });
    });

    eventSource.addEventListener('ATTENDANCE_CORRECTED', () => {
      refetchSummary();
      refetchRecords();
      queryClient.invalidateQueries({ queryKey: ['drive', driveId] });
    });

    eventSource.onerror = () => {
      setSseConnected(false);
      eventSource.close();
    };

    return () => {
      eventSource.close();
      setSseConnected(false);
    };
  }, [driveId, queryClient, refetchRecords, refetchSummary]);

  // Lifecycle Mutations
  const scheduleMutation = useMutation({
    mutationFn: () => apiScheduleDrive(driveId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drive', driveId] });
    },
    onError: (err: any) => {
      alert(err.response?.data?.message || 'Failed to schedule drive');
    },
  });

  const openMutation = useMutation({
    mutationFn: () => apiOpenDrive(driveId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drive', driveId] });
    },
    onError: (err: any) => {
      alert(err.response?.data?.message || 'Failed to open drive');
    },
  });

  const closeMutation = useMutation({
    mutationFn: () => apiCloseDrive(driveId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drive', driveId] });
    },
    onError: (err: any) => {
      alert(err.response?.data?.message || 'Failed to close drive');
    },
  });

  const finalizeMutation = useMutation({
    mutationFn: () => apiFinalizeDrive(driveId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['drive', driveId] });
    },
    onError: (err: any) => {
      alert(err.response?.data?.message || 'Failed to finalize drive');
    },
  });

  // Excel Upload & Preview
  const handleUploadFile = async () => {
    if (!selectedFile) return;
    setUploadError(null);
    setUploadLoading(true);

    try {
      const preview = await apiUploadExcel(driveId, selectedFile);
      setUploadPreview(preview);
    } catch (err: any) {
      setUploadError(err.response?.data?.message || 'Failed to upload Excel file. Check format and contents.');
    } finally {
      setUploadLoading(false);
    }
  };

  // Confirm Import
  const handleConfirmImport = async () => {
    if (!uploadPreview) return;
    try {
      await apiConfirmImport(driveId, uploadPreview.importJobId);
      alert('Eligibility list confirmed and committed successfully!');
      setUploadPreview(null);
      setSelectedFile(null);
      queryClient.invalidateQueries({ queryKey: ['drive', driveId] });
      refetchSummary();
      refetchRecords();
      setActiveTab('attendance');
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to confirm import');
    }
  };

  // Submit Manual Correction
  const handleCorrectionSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!correctionStudent || !correctionReason.trim()) return;
    setCorrectionError(null);

    try {
      await apiRecordCorrection(driveId, {
        studentId: correctionStudent.id,
        newStatus: correctionNewStatus,
        reason: correctionReason,
      });
      setCorrectionStudent(null);
      setCorrectionReason('');
      refetchSummary();
      refetchRecords();
    } catch (err: any) {
      setCorrectionError(err.response?.data?.message || 'Failed to submit correction.');
    }
  };

  // Export Report
  const handleExportReport = async () => {
    setExportLoading(true);
    try {
      await apiExportDriveReport(driveId, includeAudit);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to export report');
    } finally {
      setExportLoading(false);
    }
  };

  if (driveLoading || !drive) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-12 text-center text-slate-500">
        <div className="w-8 h-8 border-2 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto mb-2" />
        Loading drive details...
      </div>
    );
  }

  const attendancePercent = summary?.attendancePercentage ?? 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Back button and title */}
      <div>
        <Link
          to="/drives"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-slate-900 transition-colors mb-4"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          Back to Recruitment Drives
        </Link>

        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
          <div>
            <div className="flex items-center gap-3">
              <span className="text-xs font-bold uppercase tracking-wider text-blue-600">{drive.companyName}</span>
              <StatusBadge status={drive.status} />
              {sseConnected && (
                <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping" />
                  Live SSE Connected
                </span>
              )}
            </div>
            <h1 className="text-2xl font-bold text-slate-900 mt-1">{drive.driveTitle}</h1>
            <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500 mt-2">
              <div className="flex items-center gap-1.5">
                <Calendar className="w-3.5 h-3.5 text-slate-400" />
                <span>{drive.driveDate}</span>
              </div>
              <div className="flex items-center gap-1.5">
                <MapPin className="w-3.5 h-3.5 text-slate-400" />
                <span>{drive.venue}</span>
              </div>
            </div>
          </div>

          {/* Lifecycle Action Buttons */}
          <div className="flex flex-wrap items-center gap-2">
            {drive.status === 'DRAFT' && (
              <button
                onClick={() => scheduleMutation.mutate()}
                disabled={scheduleMutation.isPending}
                className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md shadow-blue-500/20 transition-all"
              >
                Schedule Drive
              </button>
            )}

            {drive.status === 'SCHEDULED' && (
              <button
                onClick={() => openMutation.mutate()}
                disabled={openMutation.isPending}
                className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold shadow-md shadow-emerald-500/20 transition-all flex items-center gap-1.5"
              >
                <Radio className="w-3.5 h-3.5" />
                Open Attendance Window
              </button>
            )}

            {drive.status === 'OPEN' && (
              <button
                onClick={() => closeMutation.mutate()}
                disabled={closeMutation.isPending}
                className="px-4 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold shadow-md shadow-amber-500/20 transition-all"
              >
                Close Attendance Window
              </button>
            )}

            {drive.status === 'CLOSED' && (
              <>
                <button
                  onClick={() => openMutation.mutate()}
                  disabled={openMutation.isPending}
                  className="px-3.5 py-2 rounded-xl border border-slate-300 hover:bg-slate-50 text-slate-700 text-xs font-semibold transition-all"
                >
                  Re-Open Window
                </button>
                <button
                  onClick={() => finalizeMutation.mutate()}
                  disabled={finalizeMutation.isPending}
                  className="px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold shadow-md shadow-purple-500/20 transition-all"
                >
                  Finalize Drive
                </button>
              </>
            )}

            {drive.status === 'FINALIZED' && (
              <span className="text-xs text-slate-400 font-medium italic">
                Locked &bull; Audited corrections only
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Real-Time KPI Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Eligible Students</div>
          <div className="text-2xl font-bold text-slate-900 mt-1">{summary?.totalEligible ?? drive.eligibleCount}</div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-emerald-600 uppercase tracking-wider">Present (Verified)</div>
          <div className="text-2xl font-bold text-emerald-700 mt-1">{summary?.presentCount ?? drive.presentCount}</div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="text-xs font-semibold text-rose-600 uppercase tracking-wider">Absent Students</div>
          <div className="text-2xl font-bold text-rose-700 mt-1">{summary?.absentCount ?? Math.max(0, drive.eligibleCount - drive.presentCount)}</div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <div className="text-xs font-semibold text-blue-600 uppercase tracking-wider">Attendance Rate</div>
            <Percent className="w-4 h-4 text-blue-500" />
          </div>
          <div className="text-2xl font-bold text-slate-900 mt-1">{attendancePercent.toFixed(1)}%</div>
          <div className="w-full bg-slate-100 rounded-full h-1.5 mt-2">
            <div
              className="bg-blue-600 h-1.5 rounded-full transition-all duration-500"
              style={{ width: `${Math.min(100, attendancePercent)}%` }}
            />
          </div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="flex items-center gap-2 border-b border-slate-200">
        <button
          onClick={() => setActiveTab('attendance')}
          className={`flex items-center gap-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all ${
            activeTab === 'attendance'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <UserCheck className="w-4 h-4" />
          Live Attendance & Search ({records.length})
        </button>

        <button
          onClick={() => setActiveTab('import')}
          className={`flex items-center gap-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all ${
            activeTab === 'import'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Upload className="w-4 h-4" />
          Excel Eligibility Hub (.xlsx)
        </button>

        <button
          onClick={() => setActiveTab('export')}
          className={`flex items-center gap-2 py-3 px-4 text-xs font-semibold border-b-2 transition-all ${
            activeTab === 'export'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Download className="w-4 h-4" />
          Reports & Multi-Sheet Export
        </button>
      </div>

      {/* TAB 1: Live Attendance & Search */}
      {activeTab === 'attendance' && (
        <div className="space-y-4">
          {/* Search & Filter Toolbar */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4">
            <div className="relative flex-1 max-w-md">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
              <input
                type="text"
                placeholder="Search by student roll number, name, or email..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-4 py-2 bg-white rounded-xl border border-slate-200 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
              />
            </div>

            <div className="flex items-center gap-1.5">
              {(['ALL', 'PRESENT', 'ABSENT'] as const).map((st) => (
                <button
                  key={st}
                  onClick={() => setStatusFilter(st)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                    statusFilter === st
                      ? 'bg-slate-900 text-white'
                      : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>
          </div>

          {/* Table */}
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-50/75 border-b border-slate-200 text-slate-500 font-semibold uppercase tracking-wider text-[11px]">
                    <th className="py-3 px-4">Student ID / Roll</th>
                    <th className="py-3 px-4">Name</th>
                    <th className="py-3 px-4">Institutional Email</th>
                    <th className="py-3 px-4">Contact</th>
                    <th className="py-3 px-4">Department</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Verification</th>
                    <th className="py-3 px-4">Time</th>
                    <th className="py-3 px-4 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {records.length === 0 ? (
                    <tr>
                      <td colSpan={9} className="py-12 text-center text-slate-400">
                        No student records found matching filter.
                      </td>
                    </tr>
                  ) : (
                    records.map((r) => (
                      <tr key={r.studentId} className="hover:bg-slate-50/50 transition-colors">
                        <td className="py-3 px-4 font-mono font-medium text-slate-900">{r.studentRollNumber}</td>
                        <td className="py-3 px-4 font-semibold text-slate-800">{r.studentName}</td>
                        <td className="py-3 px-4 text-slate-600">{r.email}</td>
                        <td className="py-3 px-4 text-slate-500">{r.contactNumber}</td>
                        <td className="py-3 px-4 text-slate-500">{r.department}</td>
                        <td className="py-3 px-4">
                          <StatusBadge status={r.status} />
                        </td>
                        <td className="py-3 px-4">
                          {r.status === 'PRESENT' && <StatusBadge status={r.verificationStatus as any} />}
                        </td>
                        <td className="py-3 px-4 text-slate-500 font-mono text-[11px]">
                          {r.recordedAt ? new Date(r.recordedAt).toLocaleTimeString() : '—'}
                        </td>
                        <td className="py-3 px-4 text-right">
                          <button
                            onClick={() => {
                              setCorrectionStudent({
                                id: r.studentId,
                                name: r.studentName,
                                roll: r.studentRollNumber,
                                currentStatus: r.status,
                              });
                              setCorrectionNewStatus(r.status === 'PRESENT' ? 'ABSENT' : 'PRESENT');
                              setCorrectionReason('');
                              setCorrectionError(null);
                            }}
                            className="px-2.5 py-1 rounded-lg text-slate-600 hover:text-blue-600 hover:bg-blue-50 border border-slate-200 text-[11px] font-semibold transition-colors"
                          >
                            Override
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: Excel Eligibility Import Hub */}
      {activeTab === 'import' && (
        <div className="space-y-6">
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
            <h2 className="text-base font-bold text-slate-900 mb-1">Upload Eligible Students List (.xlsx only)</h2>
            <p className="text-xs text-slate-500 mb-6">
              Download the official template, fill in eligible student rolls, and upload. Files are strictly validated
              against master data, zip bombs, and formula injection.
            </p>

            <div className="flex flex-wrap items-center gap-4 mb-6">
              <button
                onClick={apiDownloadTemplate}
                className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200/80 text-slate-700 text-xs font-semibold transition-colors"
              >
                <Download className="w-4 h-4 text-slate-500" />
                Download Blank Template (.xlsx)
              </button>
            </div>

            {/* File Dropzone */}
            <div className="border-2 border-dashed border-slate-200 rounded-2xl p-8 text-center hover:border-blue-400 transition-colors">
              <FileSpreadsheet className="w-10 h-10 text-slate-400 mx-auto mb-2" />
              <div className="text-xs font-semibold text-slate-700">
                {selectedFile ? selectedFile.name : 'Choose or drag & drop an .xlsx file'}
              </div>
              <p className="text-[11px] text-slate-400 mt-1">Maximum 5 MB &bull; Up to 5,000 rows &bull; .xlsx only</p>
              <input
                type="file"
                accept=".xlsx"
                id="excelInput"
                onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                className="hidden"
              />
              <div className="mt-4 flex items-center justify-center gap-3">
                <label
                  htmlFor="excelInput"
                  className="px-4 py-2 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-700 text-xs font-semibold cursor-pointer transition-colors"
                >
                  Select File
                </label>
                {selectedFile && (
                  <button
                    onClick={handleUploadFile}
                    disabled={uploadLoading}
                    className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md shadow-blue-500/20 transition-all disabled:opacity-50"
                  >
                    {uploadLoading ? 'Validating...' : 'Upload & Preview'}
                  </button>
                )}
              </div>
            </div>

            {uploadError && (
              <div className="mt-4 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-start gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{uploadError}</span>
              </div>
            )}
          </div>

          {/* Import Preview Modal / Section */}
          {uploadPreview && (
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 pb-4">
                <div>
                  <h3 className="text-base font-bold text-slate-900">Upload Validation Preview</h3>
                  <p className="text-xs text-slate-500">File: {uploadPreview.filename}</p>
                </div>
                <div className="flex items-center gap-3">
                  <div className="px-3 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-semibold">
                    Total: {uploadPreview.totalRows}
                  </div>
                  <div className="px-3 py-1 rounded-lg bg-emerald-100 text-emerald-800 text-xs font-semibold">
                    Valid: {uploadPreview.validRows}
                  </div>
                  <div className="px-3 py-1 rounded-lg bg-rose-100 text-rose-800 text-xs font-semibold">
                    Rejected: {uploadPreview.invalidRows}
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex flex-wrap items-center gap-3">
                <button
                  onClick={handleConfirmImport}
                  disabled={uploadPreview.validRows === 0}
                  className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold shadow-md shadow-emerald-500/20 transition-all disabled:opacity-50"
                >
                  Confirm & Commit {uploadPreview.validRows} Students
                </button>

                {uploadPreview.invalidRows > 0 && (
                  <button
                    onClick={() => apiDownloadRejectedRows(driveId, uploadPreview.importJobId)}
                    className="px-4 py-2 rounded-xl bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-semibold transition-colors flex items-center gap-1.5"
                  >
                    <Download className="w-3.5 h-3.5" />
                    Download Rejected Rows (.xlsx)
                  </button>
                )}
              </div>

              {/* Error Inspector Table */}
              {uploadPreview.errors.length > 0 && (
                <div className="mt-4">
                  <h4 className="text-xs font-bold text-rose-600 uppercase tracking-wider mb-2">
                    Validation Failures Inspector ({uploadPreview.errors.length} Rows)
                  </h4>
                  <div className="border border-rose-200 rounded-xl overflow-hidden text-xs">
                    <table className="w-full text-left">
                      <thead className="bg-rose-50/70 border-b border-rose-200 text-rose-900 font-semibold">
                        <tr>
                          <th className="py-2.5 px-3">Row</th>
                          <th className="py-2.5 px-3">Student ID</th>
                          <th className="py-2.5 px-3">Name</th>
                          <th className="py-2.5 px-3">Email</th>
                          <th className="py-2.5 px-3">Error Reason</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-rose-100">
                        {uploadPreview.errors.map((err, idx) => (
                          <tr key={idx} className="hover:bg-rose-50/30">
                            <td className="py-2 px-3 font-mono font-bold text-slate-700">{err.rowNumber}</td>
                            <td className="py-2 px-3 font-mono text-slate-700">{err.studentId || '—'}</td>
                            <td className="py-2 px-3 text-slate-700">{err.studentName || '—'}</td>
                            <td className="py-2 px-3 text-slate-500">{err.email || '—'}</td>
                            <td className="py-2 px-3 text-rose-600 font-medium">{err.errorReason}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: Reports & Multi-Sheet Export */}
      {activeTab === 'export' && (
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm max-w-xl">
          <h2 className="text-base font-bold text-slate-900 mb-1">Export Attendance Workbook (.xlsx)</h2>
          <p className="text-xs text-slate-500 mb-6">
            Generates a streaming Apache POI SXSSFWorkbook with verified database count parity. Includes sheets for
            Summary, Present, Absent, and All Attendance.
          </p>

          <div className="space-y-4">
            <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 hover:bg-slate-50 cursor-pointer transition-colors">
              <input
                type="checkbox"
                checked={includeAudit}
                onChange={(e) => setIncludeAudit(e.target.checked)}
                className="w-4 h-4 text-blue-600 rounded border-slate-300 focus:ring-blue-500"
              />
              <div>
                <span className="text-xs font-semibold text-slate-800 block">
                  Include Verification Audit Sheet
                </span>
                <span className="text-[11px] text-slate-500 block">
                  Officer-only: exports challenge attempts, verification outcomes, and reasons (sanitized of keys).
                </span>
              </div>
            </label>

            <button
              onClick={handleExportReport}
              disabled={exportLoading}
              className="w-full py-2.5 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md shadow-blue-500/20 flex items-center justify-center gap-2 transition-all disabled:opacity-50"
            >
              {exportLoading ? (
                'Generating Workbook...'
              ) : (
                <>
                  <Download className="w-4 h-4" />
                  <span>Download Complete Report (.xlsx)</span>
                </>
              )}
            </button>
          </div>
        </div>
      )}

      {/* Audited Manual Override Correction Modal */}
      {correctionStudent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 border border-slate-100 animate-in fade-in zoom-in-95 duration-150">
            <h3 className="text-base font-bold text-slate-900 mb-1">Audited Attendance Override</h3>
            <p className="text-xs text-slate-500 mb-4">
              Student: <strong>{correctionStudent.name}</strong> ({correctionStudent.roll})
            </p>

            {correctionError && (
              <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs">
                {correctionError}
              </div>
            )}

            <form onSubmit={handleCorrectionSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Target Status</label>
                <div className="flex items-center gap-3">
                  <label className="flex items-center gap-1.5 text-xs text-slate-700">
                    <input
                      type="radio"
                      name="status"
                      checked={correctionNewStatus === 'PRESENT'}
                      onChange={() => setCorrectionNewStatus('PRESENT')}
                      className="text-blue-600"
                    />
                    Mark PRESENT
                  </label>
                  <label className="flex items-center gap-1.5 text-xs text-slate-700">
                    <input
                      type="radio"
                      name="status"
                      checked={correctionNewStatus === 'ABSENT'}
                      onChange={() => setCorrectionNewStatus('ABSENT')}
                      className="text-blue-600"
                    />
                    Mark ABSENT
                  </label>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Mandatory Justification Reason
                </label>
                <textarea
                  required
                  rows={3}
                  placeholder="e.g. Physical college ID card verified in person at T&P Help Desk due to sensor failure."
                  value={correctionReason}
                  onChange={(e) => setCorrectionReason(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl border border-slate-200 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setCorrectionStudent(null)}
                  className="px-3.5 py-1.5 rounded-xl border border-slate-200 text-slate-600 text-xs font-semibold hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md shadow-blue-500/20"
                >
                  Submit Override
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
