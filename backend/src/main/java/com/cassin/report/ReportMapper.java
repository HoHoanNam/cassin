package com.cassin.report;

import org.springframework.stereotype.Component;

import com.cassin.report.dto.AdminReportResponse;
import com.cassin.report.dto.ReportResponse;
import com.cassin.user.User;
import com.cassin.vocab.Sense;

/**
 * Đổi ContentReport → DTO. Một chỗ duy nhất biết DTO lấy dữ liệu từ những entity nào
 * (report, sense, word, user), DTO chỉ còn là dữ liệu.
 *
 * Mapper đọc quan hệ LAZY (sense.word, reporter...) → phải gọi BÊN TRONG transaction của service
 * và các quan hệ nên được fetch sẵn (EntityGraph / join fetch trong repository), nếu không sẽ N+1.
 */
@Component
public class ReportMapper {
  public ReportResponse toResponse(ContentReport r) {
    Sense sense = r.getSense();
    return new ReportResponse(
        r.getId(),
        sense.getId(),
        sense.getWord().getTerm(),
        sense.getDefinition(),
        r.getReason(),
        r.getMessage(),
        r.getStatus(),
        r.getAdminNote(),
        r.getCreatedAt(),
        // [SỬA] getUpdatedAt() → getResolvedAt().
        // updatedAt luôn có giá trị → báo cáo đang OPEN cũng hiện "đã xử lý lúc ..." trên FE.
        r.getResolvedAt());
  }

  public AdminReportResponse toAdminResponse(ContentReport r) {
    Sense sense = r.getSense();
    // [SỬA] resolvedBy NULL khi báo cáo còn OPEN (hoặc admin đã bị xóa: ON DELETE SET NULL).
    // Bản cũ gọi thẳng r.getResolvedBy().getDisplayName() → NullPointerException
    // → GET /api/admin/reports trả 500 ngay khi có một báo cáo đang mở.
    User resolvedBy = r.getResolvedBy();
    return new AdminReportResponse(
        r.getId(),
        sense.getId(),
        sense.getWord().getTerm(),
        sense.getDefinition(),
        r.getReason(),
        r.getMessage(),
        r.getStatus(),
        r.getAdminNote(),
        r.getCreatedAt(),
        r.getResolvedAt(), // [SỬA] như toResponse
        r.getReporter().getId(),
        r.getReporter().getDisplayName(),
        resolvedBy == null ? null : resolvedBy.getDisplayName());
  }
}
