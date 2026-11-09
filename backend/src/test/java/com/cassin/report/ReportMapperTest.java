package com.cassin.report;

import static com.cassin.report.ReportFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cassin.report.dto.AdminReportResponse;
import com.cassin.report.dto.ReportResponse;

/**
 * ReportMapper: kiểm tra từng field đi ĐÚNG chỗ.
 * DTO là record dựng theo VỊ TRÍ tham số: đảo hai field cùng kiểu (createdAt / resolvedAt) vẫn biên dịch được,
 * chỉ test như thế này mới bắt được.
 */
class ReportMapperTest {
  private final ReportMapper mapper = new ReportMapper();

  @Test
  @DisplayName("toResponse báo cáo OPEN: lấy term / nghĩa từ sense, resolvedAt null (KHÔNG phải updatedAt)")
  void toResponse_open() {
    ContentReport report = ReportFixtures.openReport(5L, ReportFixtures.learner(), ReportFixtures.systemSense());

    ReportResponse dto = mapper.toResponse(report);

    assertThat(dto.id()).isEqualTo(5L);
    assertThat(dto.senseId()).isEqualTo(60L);
    assertThat(dto.term()).isEqualTo("break the ice");
    assertThat(dto.definition()).isEqualTo("phá vỡ sự ngại ngùng ban đầu");
    assertThat(dto.reason()).isEqualTo(ReportReason.TYPO);
    assertThat(dto.message()).isEqualTo("Sai chính tả");
    assertThat(dto.status()).isEqualTo(ReportStatus.OPEN);
    assertThat(dto.adminNote()).isNull();
    assertThat(dto.createdAt()).isEqualTo(NOW);
    assertThat(dto.resolvedAt()).isNull();
  }

  @Test
  @DisplayName("toAdminResponse báo cáo OPEN: resolvedBy null → resolvedByName null, KHÔNG NullPointerException")
  void toAdminResponse_open_noResolver() {
    ContentReport report = ReportFixtures.openReport(5L, ReportFixtures.learner(), ReportFixtures.systemSense());

    AdminReportResponse dto = mapper.toAdminResponse(report);

    assertThat(dto.reporterId()).isEqualTo(ReportFixtures.USER_ID);
    assertThat(dto.reporterName()).isEqualTo("Học viên An");
    assertThat(dto.resolvedByName()).isNull();
    assertThat(dto.resolvedAt()).isNull();
  }

  @Test
  @DisplayName("toAdminResponse báo cáo đã xử lý: có tên admin, ghi chú, thời điểm xử lý")
  void toAdminResponse_resolved() {
    ContentReport report = ReportFixtures.openReport(5L, ReportFixtures.learner(), ReportFixtures.systemSense());
    report.resolve(ReportFixtures.admin(), "Đã sửa", NOW.plusSeconds(60));

    AdminReportResponse dto = mapper.toAdminResponse(report);

    assertThat(dto.status()).isEqualTo(ReportStatus.RESOLVED);
    assertThat(dto.adminNote()).isEqualTo("Đã sửa");
    assertThat(dto.resolvedByName()).isEqualTo("Quản trị");
    assertThat(dto.resolvedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(dto.createdAt()).isEqualTo(NOW);
  }
}
