/**
 * [THÊM] Báo lỗi nội dung. Khớp 1-1 với package com.cassin.report.dto ở backend.
 *
 *   OPEN ──admin "Đã sửa"──▶ RESOLVED
 *     │  └──admin "Từ chối"──▶ REJECTED (bắt buộc ghi lý do)
 *     └──học viên rút──▶ (xóa)
 */

/** Khớp enum ReportReason */
export type ReportReason = 'WRONG_DEFINITION' | 'WRONG_EXAMPLE' | 'WRONG_PART_OF_SPEECH' | 'TYPO' | 'OTHER';

/** Khớp enum ReportStatus */
export type ReportStatus = 'OPEN' | 'RESOLVED' | 'REJECTED';

/** Khớp CreateReportRequest: POST /api/reports */
export interface CreateReportRequest {
  senseId: number;
  reason: ReportReason;
  message: string | null;
}

/** Khớp ReportResponse: báo cáo của chính người học */
export interface Report {
  id: number;
  senseId: number;
  term: string;
  definition: string;
  reason: ReportReason;
  message: string | null;
  status: ReportStatus;
  adminNote: string | null;
  /** ISO-8601 (Instant của Java) */
  createdAt: string;
  resolvedAt: string | null;
}

/** Khớp AdminReportResponse: thêm người báo và người xử lý */
export interface AdminReport extends Report {
  reporterId: number;
  reporterName: string;
  resolvedByName: string | null;
}

/** Khớp ReviewReportRequest: PATCH /api/admin/reports/{id}. Admin chỉ chuyển sang RESOLVED hoặc REJECTED. */
export interface ReviewReportRequest {
  status: Exclude<ReportStatus, 'OPEN'>;
  adminNote: string | null;
}

/** Khớp @Size(max = 500) của message / adminNote (ContentReport.MAX_TEXT) */
export const REPORT_TEXT_MAX = 500;

export const REPORT_REASONS: { value: ReportReason; label: string; hint: string }[] = [
  { value: 'WRONG_DEFINITION', label: 'Nghĩa sai', hint: 'Nghĩa tiếng Việt không đúng với từ' },
  { value: 'WRONG_EXAMPLE', label: 'Câu ví dụ sai', hint: 'Câu sai ngữ pháp, không tự nhiên, hoặc không dùng nghĩa này' },
  { value: 'WRONG_PART_OF_SPEECH', label: 'Sai loại từ', hint: 'Ví dụ ghi là động từ nhưng thật ra là danh từ' },
  { value: 'TYPO', label: 'Lỗi chính tả', hint: 'Gõ sai chữ, thiếu dấu, sai phiên âm' },
  { value: 'OTHER', label: 'Khác', hint: 'Mô tả cụ thể ở ô bên dưới' },
];

export const REPORT_REASON_LABEL: Record<ReportReason, string> = Object.fromEntries(
  REPORT_REASONS.map((r) => [r.value, r.label]),
) as Record<ReportReason, string>;

export const REPORT_STATUS_LABEL: Record<ReportStatus, string> = {
  OPEN: 'Đang chờ xử lý',
  RESOLVED: 'Đã sửa',
  REJECTED: 'Không sửa',
};
