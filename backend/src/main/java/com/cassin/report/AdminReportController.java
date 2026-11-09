package com.cassin.report;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.report.dto.AdminReportResponse;
import com.cassin.report.dto.ReviewReportRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Báo lỗi nội dung, phía admin.
 * Không cần @PreAuthorize: SecurityConfig đã khóa /api/admin/** cho ROLE_ADMIN (học viên gọi → 403).
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {
  private final AdminReportService adminReportService;

  /**
   * GET /api/admin/reports?status=OPEN&page=0&size=20
   * required = false: không truyền status → xem tất cả.
   * Spring tự đổi chuỗi "OPEN" sang enum; sai chữ ("open1") → MethodArgumentTypeMismatchException
   * → 400 INVALID_PARAMETER nhờ handler có sẵn trong GlobalExceptionHandler.
   */
  @GetMapping
  public PageResponse<AdminReportResponse> list(
      @RequestParam(required = false) ReportStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return adminReportService.list(status, page, size);
  }

  /** Số báo cáo đang chờ xử lý. Đặt TRƯỚC "/{reportId}" cũng được: Spring ưu tiên đường dẫn cố định. */
  @GetMapping("/open-count")
  public long openCount() {
    return adminReportService.countOpen();
  }

  /**
   * PATCH /api/admin/reports/{reportId}   body: { "status": "RESOLVED" | "REJECTED", "adminNote": "..." }
   * PATCH (không phải PUT): chỉ đổi MỘT PHẦN báo cáo (trạng thái + ghi chú), không gửi lại cả tài nguyên.
   * adminId lấy từ token, không bao giờ nhận từ body.
   */
  @PatchMapping("/{reportId}")
  public AdminReportResponse review(
      @PathVariable Long reportId,
      @Valid @RequestBody ReviewReportRequest request,
      @AuthenticationPrincipal AuthUser admin) {
    return adminReportService.review(reportId, request, admin.id());
  }
}
