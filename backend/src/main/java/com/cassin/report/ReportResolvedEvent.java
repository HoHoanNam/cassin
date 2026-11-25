package com.cassin.report;

import com.cassin.badge.event.BadgeTrigger;

/**
 * Phát khi admin xác nhận một báo lỗi là ĐÚNG (chuyển sang RESOLVED, AdminReportService.review).
 * Từ chối (REJECTED) không phát: báo sai không được thưởng.
 *
 * userId = NGƯỜI BÁO LỖI (người nhận huy hiệu Mắt tinh), KHÔNG phải admin đang thao tác (xem BadgeTrigger).
 */
public record ReportResolvedEvent(Long userId, Long reportId) implements BadgeTrigger {
}
