import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ReportApi } from '../api/report.api';
import { PageResponse } from '../models/common.models';
import {
  AdminReport,
  CreateReportRequest,
  REPORT_TEXT_MAX,
  Report,
  ReportStatus,
  ReviewReportRequest,
} from '../models/report.models';
import { DbReport, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { businessError, conflict, forbidden, notFound, simulate, slicePage, tooLong, validate } from './mock-utils';

/**
 * [THÊM] Giả lập ReportService + AdminReportService của backend, cùng luật và cùng mã lỗi:
 *
 *   1. nghĩa phải tồn tại và đọc được             → 404 SENSE_NOT_FOUND
 *   2. chỉ báo từ HỆ THỐNG                         → 400 REPORT_OWN_WORD
 *   3. không 2 báo cáo đang mở cho cùng nghĩa      → 409 REPORT_EXISTS
 *   4. chỉ rút báo cáo của chính mình              → 404 REPORT_NOT_FOUND
 *   5. chỉ rút báo cáo đang OPEN                   → 409 REPORT_CLOSED
 *   6. admin chỉ chuyển sang RESOLVED / REJECTED   → 400 INVALID_STATUS
 *   7. từ chối phải có lý do                       → 400 NOTE_REQUIRED
 *   +  báo cáo đã đóng thì không xử lý lại         → 409 REPORT_CLOSED
 */
@Injectable()
export class MockReportApi extends ReportApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  create(request: CreateReportRequest): Observable<Report> {
    return simulate(() => {
      const userId = this.session.userId();
      validate([
        ['senseId', request.senseId == null, 'Thiếu nghĩa cần báo'],
        ['reason', request.reason == null, 'Chọn lý do báo lỗi'],
        ['message', tooLong(request.message, REPORT_TEXT_MAX), 'Mô tả tối đa 500 ký tự'],
      ]);
      const sense = this.db.senses.find((s) => s.id === request.senseId);
      const word = sense && this.db.words.find((w) => w.id === sense.wordId);
      if (!sense || !word || (word.ownerId !== null && word.ownerId !== userId)) {
        throw notFound('SENSE_NOT_FOUND', 'Không tìm thấy nghĩa');
      }
      if (word.ownerId !== null) {
        throw businessError('REPORT_OWN_WORD', 'Đây là từ của bạn, bạn có thể tự sửa trực tiếp');
      }
      if (this.db.reports.some((r) => r.reporterId === userId && r.senseId === sense.id && r.status === 'OPEN')) {
        throw conflict('REPORT_EXISTS', 'Bạn đã báo lỗi nghĩa này rồi');
      }
      const report: DbReport = {
        id: this.db.nextId(),
        reporterId: userId,
        senseId: sense.id,
        reason: request.reason,
        message: blankToNull(request.message),
        status: 'OPEN',
        adminNote: null,
        resolvedById: null,
        createdAt: new Date().toISOString(),
        resolvedAt: null,
      };
      this.db.reports.push(report);
      this.db.save();
      return this.toReport(report);
    });
  }

  listMine(page: number, size: number): Observable<PageResponse<Report>> {
    return simulate(() => {
      const userId = this.session.userId();
      const mine = this.newestFirst(this.db.reports.filter((r) => r.reporterId === userId));
      return slicePage(mine.map((r) => this.toReport(r)), page, size);
    });
  }

  withdraw(reportId: number): Observable<void> {
    return simulate(() => {
      const userId = this.session.userId();
      // Không có, hoặc của người khác: CÙNG một lỗi 404 (không lộ "id này có tồn tại").
      const report = this.db.reports.find((r) => r.id === reportId && r.reporterId === userId);
      if (!report) {
        throw notFound('REPORT_NOT_FOUND', 'Không tìm thấy báo cáo');
      }
      if (report.status !== 'OPEN') {
        throw conflict('REPORT_CLOSED', 'Báo cáo này đã được xử lý, không thể rút');
      }
      this.db.removeWhere('reports', (r) => r.id === reportId);
      this.db.save();
    });
  }

  adminList(status: ReportStatus | null, page: number, size: number): Observable<PageResponse<AdminReport>> {
    return simulate(() => {
      this.requireAdmin();
      const rows = this.newestFirst(this.db.reports.filter((r) => status === null || r.status === status));
      return slicePage(rows.map((r) => this.toAdminReport(r)), page, size);
    });
  }

  openCount(): Observable<number> {
    return simulate(() => {
      this.requireAdmin();
      return this.db.reports.filter((r) => r.status === 'OPEN').length;
    });
  }

  review(reportId: number, request: ReviewReportRequest): Observable<AdminReport> {
    return simulate(() => {
      const adminId = this.requireAdmin();
      validate([
        ['status', request.status == null, 'Chọn kết quả xử lý'],
        ['adminNote', tooLong(request.adminNote, REPORT_TEXT_MAX), 'Ghi chú tối đa 500 ký tự'],
      ]);
      const report = this.db.reports.find((r) => r.id === reportId);
      if (!report) {
        throw notFound('REPORT_NOT_FOUND', 'Không tìm thấy báo cáo');
      }
      if ((request.status as ReportStatus) === 'OPEN') {
        throw businessError('INVALID_STATUS', 'Chỉ chuyển được sang RESOLVED hoặc REJECTED');
      }
      if (report.status !== 'OPEN') {
        throw conflict('REPORT_CLOSED', 'Báo cáo này đã được xử lý');
      }
      const note = blankToNull(request.adminNote);
      if (request.status === 'REJECTED' && note === null) {
        throw businessError('NOTE_REQUIRED', 'Cần ghi lý do từ chối');
      }
      Object.assign(report, {
        status: request.status,
        adminNote: note,
        resolvedById: adminId,
        resolvedAt: new Date().toISOString(),
      });
      this.db.save();
      return this.toAdminReport(report);
    });
  }

  // ===== helper =====

  private requireAdmin(): number {
    const user = this.db.users.find((u) => u.id === this.session.userId());
    if (user?.role !== 'ADMIN') {
      throw forbidden('FORBIDDEN', 'Bạn không có quyền thực hiện thao tác này');
    }
    return user.id;
  }

  private newestFirst(rows: DbReport[]): DbReport[] {
    return [...rows].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  /** ReportMapper.toResponse */
  private toReport(r: DbReport): Report {
    const sense = this.db.senses.find((s) => s.id === r.senseId);
    const word = sense && this.db.words.find((w) => w.id === sense.wordId);
    return {
      id: r.id,
      senseId: r.senseId,
      term: word?.term ?? '',
      definition: sense?.definition ?? '',
      reason: r.reason,
      message: r.message,
      status: r.status,
      adminNote: r.adminNote,
      createdAt: r.createdAt,
      resolvedAt: r.resolvedAt,
    };
  }

  /** ReportMapper.toAdminResponse */
  private toAdminReport(r: DbReport): AdminReport {
    const reporter = this.db.users.find((u) => u.id === r.reporterId);
    const resolvedBy = r.resolvedById !== null ? this.db.users.find((u) => u.id === r.resolvedById) : undefined;
    return {
      ...this.toReport(r),
      reporterId: r.reporterId,
      reporterName: reporter?.displayName ?? '',
      resolvedByName: resolvedBy?.displayName ?? null,
    };
  }
}

/** ContentReport.blankToNull: cắt khoảng trắng, rỗng → null */
function blankToNull(value: string | null | undefined): string | null {
  const stripped = (value ?? '').trim();
  return stripped.length > 0 ? stripped : null;
}
