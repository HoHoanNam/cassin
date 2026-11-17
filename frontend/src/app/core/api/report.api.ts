import { Observable } from 'rxjs';

import { PageResponse } from '../models/common.models';
import { AdminReport, CreateReportRequest, Report, ReportStatus, ReviewReportRequest } from '../models/report.models';

/**
 * [THÊM] Báo lỗi nội dung: học viên báo một NGHĨA của từ hệ thống bị sai, admin xử lý.
 * Gom cả hai phía vào một API giống module com.cassin.report ở backend.
 */
export abstract class ReportApi {
  // ---------- Học viên: /api/reports ----------
  /**
   * POST /api/reports → 201
   * Lỗi: 404 SENSE_NOT_FOUND · 400 REPORT_OWN_WORD (từ của mình thì tự sửa) · 409 REPORT_EXISTS (đã báo, đang chờ)
   */
  abstract create(request: CreateReportRequest): Observable<Report>;
  /** GET /api/reports?page=&size= (mới nhất trước) */
  abstract listMine(page: number, size: number): Observable<PageResponse<Report>>;
  /** DELETE /api/reports/{id} → 204 · 404 REPORT_NOT_FOUND · 409 REPORT_CLOSED (đã xử lý thì không rút được) */
  abstract withdraw(reportId: number): Observable<void>;

  // ---------- Quản trị: /api/admin/reports (chỉ ADMIN) ----------
  /** GET /api/admin/reports?status=&page=&size=. status = null: mọi trạng thái. */
  abstract adminList(status: ReportStatus | null, page: number, size: number): Observable<PageResponse<AdminReport>>;
  /** GET /api/admin/reports/open-count: số báo cáo đang chờ (huy hiệu trên tab) */
  abstract openCount(): Observable<number>;
  /**
   * PATCH /api/admin/reports/{id}
   * Lỗi: 400 NOTE_REQUIRED (từ chối phải có lý do) · 409 REPORT_CLOSED (admin khác đã xử lý trước)
   */
  abstract review(reportId: number, request: ReviewReportRequest): Observable<AdminReport>;
}
