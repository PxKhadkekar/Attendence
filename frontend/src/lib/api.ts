import axios from 'axios';
import type { Drive, DriveAttendanceSummary, DriveStudentRecord, ImportPreview, StudentDevice, UserProfile } from '../types';

const API_BASE = '/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach JWT token and unique Correlation ID to every outgoing request
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['X-Correlation-ID'] = 'web-' + Math.random().toString(36).substring(2, 11);
  return config;
});

// Global 401 handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

// -------------------------------------------------------------
// Auth APIs
// -------------------------------------------------------------
export const apiLogin = async (username: string, password: string) => {
  const res = await apiClient.post('/auth/login', { username, password });
  return res.data;
};

export const apiGetMe = async (): Promise<UserProfile> => {
  const res = await apiClient.get<UserProfile>('/auth/me');
  return res.data;
};

// -------------------------------------------------------------
// Drive APIs
// -------------------------------------------------------------
export const apiGetDrives = async (): Promise<{ content: Drive[]; totalElements: number }> => {
  const res = await apiClient.get('/drives?page=0&size=50');
  return res.data;
};

export const apiGetDriveById = async (id: number): Promise<Drive> => {
  const res = await apiClient.get<Drive>(`/drives/${id}`);
  return res.data;
};

export const apiCreateDrive = async (data: {
  companyName: string;
  driveTitle: string;
  driveDate: string;
  venue: string;
  description?: string;
}): Promise<Drive> => {
  const res = await apiClient.post<Drive>('/drives', data);
  return res.data;
};

export const apiScheduleDrive = async (id: number): Promise<Drive> => {
  const res = await apiClient.post<Drive>(`/drives/${id}/schedule`);
  return res.data;
};

export const apiOpenDrive = async (id: number): Promise<Drive> => {
  const res = await apiClient.post<Drive>(`/drives/${id}/open`);
  return res.data;
};

export const apiCloseDrive = async (id: number): Promise<Drive> => {
  const res = await apiClient.post<Drive>(`/drives/${id}/close`);
  return res.data;
};

export const apiFinalizeDrive = async (id: number): Promise<Drive> => {
  const res = await apiClient.post<Drive>(`/drives/${id}/finalize`);
  return res.data;
};

// -------------------------------------------------------------
// Live Attendance APIs
// -------------------------------------------------------------
export const apiGetAttendanceSummary = async (driveId: number): Promise<DriveAttendanceSummary> => {
  const res = await apiClient.get<DriveAttendanceSummary>(`/drives/${driveId}/attendance/summary`);
  return res.data;
};

export const apiGetAttendanceRecords = async (
  driveId: number,
  query = '',
  status = 'ALL'
): Promise<DriveStudentRecord[]> => {
  const res = await apiClient.get<DriveStudentRecord[]>(`/drives/${driveId}/attendance/records`, {
    params: { query, status },
  });
  return res.data;
};

export const apiRecordCorrection = async (
  driveId: number,
  data: { studentId: number; newStatus: string; reason: string }
): Promise<DriveStudentRecord> => {
  const res = await apiClient.post<DriveStudentRecord>(`/drives/${driveId}/attendance/corrections`, data);
  return res.data;
};

// -------------------------------------------------------------
// Excel Import & Export APIs
// -------------------------------------------------------------
export const apiDownloadTemplate = async () => {
  const res = await apiClient.get('/drives/import-template', {
    responseType: 'blob',
  });
  const url = window.URL.createObjectURL(new Blob([res.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', 'eligible_students_template.xlsx');
  document.body.appendChild(link);
  link.click();
  link.remove();
};

export const apiUploadExcel = async (driveId: number, file: File): Promise<ImportPreview> => {
  const formData = new FormData();
  formData.append('file', file);
  const res = await apiClient.post<ImportPreview>(`/drives/${driveId}/imports/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
};

export const apiConfirmImport = async (driveId: number, jobId: number) => {
  const res = await apiClient.post(`/drives/${driveId}/imports/${jobId}/confirm`);
  return res.data;
};

export const apiDownloadRejectedRows = async (driveId: number, jobId: number) => {
  const res = await apiClient.get(`/drives/${driveId}/imports/${jobId}/rejected-rows`, {
    responseType: 'blob',
  });
  const url = window.URL.createObjectURL(new Blob([res.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', `rejected_rows_job_${jobId}.xlsx`);
  document.body.appendChild(link);
  link.click();
  link.remove();
};

export const apiExportDriveReport = async (driveId: number, includeAudit = false) => {
  const res = await apiClient.get(`/reports/drives/${driveId}/export`, {
    params: { includeAudit },
    responseType: 'blob',
  });
  const url = window.URL.createObjectURL(new Blob([res.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', `drive_${driveId}_attendance_report.xlsx`);
  document.body.appendChild(link);
  link.click();
  link.remove();
};

// -------------------------------------------------------------
// Device Management APIs
// -------------------------------------------------------------
export const apiGetPendingDevices = async (): Promise<StudentDevice[]> => {
  const res = await apiClient.get<StudentDevice[]>('/devices/pending');
  return res.data;
};

export const apiApproveDevice = async (id: number): Promise<StudentDevice> => {
  const res = await apiClient.post<StudentDevice>(`/devices/${id}/approve`);
  return res.data;
};

export const apiRevokeDevice = async (id: number, reason: string): Promise<StudentDevice> => {
  const res = await apiClient.post<StudentDevice>(`/devices/${id}/revoke`, { reason });
  return res.data;
};
