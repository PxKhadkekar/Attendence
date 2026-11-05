export type DriveStatus = 'DRAFT' | 'SCHEDULED' | 'OPEN' | 'CLOSED' | 'FINALIZED';

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  fullName: string;
  phone?: string;
  role: 'ROLE_OFFICER' | 'ROLE_STUDENT';
  studentDatabaseId?: number;
  studentId?: string;
  department?: string;
  batchYear?: number;
}

export interface Drive {
  id: number;
  companyName: string;
  driveTitle: string;
  description?: string;
  driveDate: string;
  venue: string;
  status: DriveStatus;
  createdById: number;
  createdByName: string;
  eligibleCount: number;
  presentCount: number;
  openedAt?: string;
  closedAt?: string;
  finalizedAt?: string;
  createdAt: string;
}

export interface DriveAttendanceSummary {
  driveId: number;
  companyName: string;
  driveTitle: string;
  driveDate: string;
  venue: string;
  status: DriveStatus;
  totalEligible: number;
  presentCount: number;
  absentCount: number;
  attendancePercentage: number;
}

export interface DriveStudentRecord {
  studentId: number;
  studentRollNumber: string;
  studentName: string;
  email: string;
  contactNumber: string;
  department: string;
  status: 'PRESENT' | 'ABSENT';
  recordedAt?: string;
  verificationStatus: string;
}

export interface RowError {
  rowNumber: number;
  studentId?: string;
  studentName?: string;
  email?: string;
  contactNumber?: string;
  errorReason: string;
}

export interface ImportPreview {
  importJobId: number;
  driveId: number;
  filename: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  errors: RowError[];
}

export interface StudentDevice {
  id: number;
  studentId: number;
  studentRollNumber: string;
  studentName: string;
  deviceFingerprint: string;
  deviceModel: string;
  osVersion: string;
  publicKeyFingerprint: string;
  status: 'PENDING_APPROVAL' | 'APPROVED' | 'REVOKED';
  approvedAt?: string;
  revokedAt?: string;
  revocationReason?: string;
  createdAt: string;
}
