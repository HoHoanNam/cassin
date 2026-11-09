package com.cassin.report.dto;

import com.cassin.report.ReportStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin xử lý một báo cáo. status chỉ nhận RESOLVED hoặc REJECTED:
 * @NotNull chỉ chặn null, còn chặn OPEN là việc của AdminReportService (luật 6 → 400 INVALID_STATUS).
 */
// [SỬA] Thêm message tiếng Việt (lý do như CreateReportRequest).
public record ReviewReportRequest(
    @NotNull(message = "Chọn kết quả xử lý") ReportStatus status,
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String adminNote) {
}
