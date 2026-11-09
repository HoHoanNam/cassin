package com.cassin.report;

import static com.cassin.report.ReportFixtures.ADMIN_ID;
import static com.cassin.report.ReportFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.report.dto.AdminReportResponse;
import com.cassin.report.dto.ReviewReportRequest;
import com.cassin.user.UserRepository;

/**
 * AdminReportService: lọc danh sách + xử lý báo cáo.
 * Luật 6 (chỉ RESOLVED / REJECTED) ở service; luật 7 và "đã đóng" ở entity, service đổi thành HTTP.
 */
@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {
  private static final Instant REVIEWED_AT = NOW.plusSeconds(7200);

  @Mock ContentReportRepository reportRepository;
  @Mock UserRepository userRepository;

  private AdminReportService adminReportService;
  private ContentReport report;

  @BeforeEach
  void setUp() {
    adminReportService = new AdminReportService(
        reportRepository, userRepository, new ReportMapper(), Clock.fixed(REVIEWED_AT, ZoneOffset.UTC));
    report = ReportFixtures.openReport(5L, ReportFixtures.learner(), ReportFixtures.systemSense());
  }

  private static void assertBusiness(Throwable ex, HttpStatus status, String code) {
    assertThat(ex).isInstanceOfSatisfying(BusinessException.class, b -> {
      assertThat(b.getStatus()).isEqualTo(status);
      assertThat(b.getCode()).isEqualTo(code);
    });
  }

  // ===================== list =====================

  @Test
  @DisplayName("list(OPEN): truyền đúng status, cỡ trang bị ép về 50, mới nhất trước, map sang AdminReportResponse")
  void list_filtersAndMaps() {
    when(reportRepository.search(eq(ReportStatus.OPEN), any(Pageable.class)))
        .thenAnswer(inv -> new PageImpl<>(List.of(report), inv.getArgument(1), 1));

    PageResponse<AdminReportResponse> page = adminReportService.list(ReportStatus.OPEN, 0, 500);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(reportRepository).search(eq(ReportStatus.OPEN), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(page.content()).singleElement().satisfies(r -> {
      assertThat(r.reporterName()).isEqualTo("Học viên An");
      assertThat(r.resolvedByName()).isNull();
    });
  }

  @Test
  @DisplayName("list(null): status null đi thẳng xuống repository = xem mọi trạng thái")
  void list_nullStatus_meansAll() {
    when(reportRepository.search(isNull(), any(Pageable.class)))
        .thenAnswer(inv -> new PageImpl<ContentReport>(List.of(), inv.getArgument(1), 0));

    adminReportService.list(null, 0, 20);

    verify(reportRepository).search(isNull(), any(Pageable.class));
  }

  // ===================== review =====================

  @Test
  @DisplayName("RESOLVED: đóng báo cáo, người xử lý = admin trong token, thời điểm lấy từ Clock")
  void review_resolve() {
    when(reportRepository.findById(5L)).thenReturn(Optional.of(report));
    when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(ReportFixtures.admin());

    AdminReportResponse response = adminReportService.review(
        5L, new ReviewReportRequest(ReportStatus.RESOLVED, "Đã sửa nghĩa"), ADMIN_ID);

    assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);   // entity đổi → dirty checking lưu
    assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
    assertThat(response.resolvedByName()).isEqualTo("Quản trị");
    assertThat(response.adminNote()).isEqualTo("Đã sửa nghĩa");
    assertThat(response.resolvedAt()).isEqualTo(REVIEWED_AT);
  }

  @Test
  @DisplayName("REJECTED có lý do: đóng với trạng thái REJECTED")
  void review_reject() {
    when(reportRepository.findById(5L)).thenReturn(Optional.of(report));
    when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(ReportFixtures.admin());

    AdminReportResponse response = adminReportService.review(
        5L, new ReviewReportRequest(ReportStatus.REJECTED, "Nghĩa này đúng"), ADMIN_ID);

    assertThat(response.status()).isEqualTo(ReportStatus.REJECTED);
  }

  @Test
  @DisplayName("Luật 6: chuyển về OPEN → 400 INVALID_STATUS, báo cáo không đổi")
  void review_toOpen_isBadRequest() {
    when(reportRepository.findById(5L)).thenReturn(Optional.of(report));
    when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(ReportFixtures.admin());

    Throwable ex = catchThrowable(() ->
        adminReportService.review(5L, new ReviewReportRequest(ReportStatus.OPEN, null), ADMIN_ID));

    assertBusiness(ex, HttpStatus.BAD_REQUEST, "INVALID_STATUS");
    assertThat(report.isOpen()).isTrue();
  }

  @Test
  @DisplayName("Luật 7: REJECTED thiếu lý do → 400 NOTE_REQUIRED")
  void review_rejectWithoutNote_isBadRequest() {
    when(reportRepository.findById(5L)).thenReturn(Optional.of(report));
    when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(ReportFixtures.admin());

    Throwable ex = catchThrowable(() ->
        adminReportService.review(5L, new ReviewReportRequest(ReportStatus.REJECTED, "  "), ADMIN_ID));

    assertBusiness(ex, HttpStatus.BAD_REQUEST, "NOTE_REQUIRED");
    assertThat(report.isOpen()).isTrue();
  }

  @Test
  @DisplayName("Báo cáo đã được xử lý → 409 REPORT_CLOSED (admin khác xử lý trước)")
  void review_alreadyClosed_isConflict() {
    report.resolve(ReportFixtures.user(100L, "Admin khác"), "Xong", NOW);
    when(reportRepository.findById(5L)).thenReturn(Optional.of(report));
    when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(ReportFixtures.admin());

    Throwable ex = catchThrowable(() ->
        adminReportService.review(5L, new ReviewReportRequest(ReportStatus.REJECTED, "Sai"), ADMIN_ID));

    assertBusiness(ex, HttpStatus.CONFLICT, "REPORT_CLOSED");
    assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);   // giữ kết quả của admin trước
  }

  @Test
  @DisplayName("Không có báo cáo → 404 REPORT_NOT_FOUND")
  void review_missing_isNotFound() {
    when(reportRepository.findById(404L)).thenReturn(Optional.empty());

    Throwable ex = catchThrowable(() ->
        adminReportService.review(404L, new ReviewReportRequest(ReportStatus.RESOLVED, null), ADMIN_ID));

    assertBusiness(ex, HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND");
  }

  @Test
  @DisplayName("countOpen: đếm đúng trạng thái OPEN")
  void countOpen() {
    when(reportRepository.countByStatus(ReportStatus.OPEN)).thenReturn(3L);

    assertThat(adminReportService.countOpen()).isEqualTo(3L);
  }
}
