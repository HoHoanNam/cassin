package com.cassin.report;

import static com.cassin.report.ReportFixtures.NOW;
import static com.cassin.report.ReportFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.report.dto.CreateReportRequest;
import com.cassin.report.dto.ReportResponse;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.Sense;
import com.cassin.vocab.WordService;

/**
 * ReportService (phía học viên). Repository và WordService là MOCK, ReportMapper dùng THẬT
 * (không có phụ thuộc, mock nó chỉ làm test dài thêm mà không kiểm tra thêm được gì).
 *
 * Luật: 1 nghĩa đọc được · 2 chỉ từ hệ thống · 3 không báo trùng · 4 chỉ rút của mình · 5 chỉ rút khi OPEN
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
  private static final Long SENSE_ID = 60L;

  @Mock ContentReportRepository reportRepository;
  @Mock WordService wordService;
  @Mock UserRepository userRepository;

  private ReportService reportService;

  private final User me = ReportFixtures.learner();
  private final Sense systemSense = ReportFixtures.systemSense();

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    reportService = new ReportService(reportRepository, wordService, userRepository, new ReportMapper(), fixedClock);
  }

  private static CreateReportRequest request(Long senseId) {
    return new CreateReportRequest(senseId, ReportReason.WRONG_EXAMPLE, "Câu ví dụ sai ngữ pháp");
  }

  private static void assertBusiness(Throwable ex, HttpStatus status, String code) {
    assertThat(ex).isInstanceOfSatisfying(BusinessException.class, b -> {
      assertThat(b.getStatus()).isEqualTo(status);
      assertThat(b.getCode()).isEqualTo(code);
      assertThat(b.getMessage()).isNotBlank();   // FE luôn có câu để hiển thị
    });
  }

  // ===================== create =====================

  @Test
  @DisplayName("Báo lỗi từ hệ thống: lưu bằng saveAndFlush, trả OPEN, createdAt lấy từ Clock")
  void create_systemWord_saves() {
    when(wordService.findReadableSense(SENSE_ID, USER_ID)).thenReturn(systemSense);
    when(reportRepository.existsByReporterIdAndSenseIdAndStatus(USER_ID, SENSE_ID, ReportStatus.OPEN)).thenReturn(false);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);

    ReportResponse response = reportService.create(request(SENSE_ID), USER_ID);

    ArgumentCaptor<ContentReport> saved = ArgumentCaptor.forClass(ContentReport.class);
    verify(reportRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getReporter()).isSameAs(me);
    assertThat(saved.getValue().getSense()).isSameAs(systemSense);
    assertThat(saved.getValue().getReason()).isEqualTo(ReportReason.WRONG_EXAMPLE);

    assertThat(response.status()).isEqualTo(ReportStatus.OPEN);
    assertThat(response.term()).isEqualTo("break the ice");
    assertThat(response.createdAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("Luật 1: nghĩa không đọc được → lỗi của WordService đi thẳng ra, không chạm repository")
  void create_unreadableSense_propagates() {
    BusinessException notFound = new BusinessException(HttpStatus.NOT_FOUND, "SENSE_NOT_FOUND", "Không tìm thấy nghĩa");
    when(wordService.findReadableSense(SENSE_ID, USER_ID)).thenThrow(notFound);

    assertThatThrownBy(() -> reportService.create(request(SENSE_ID), USER_ID)).isSameAs(notFound);
    verifyNoInteractions(reportRepository);
  }

  @Test
  @DisplayName("Luật 2: báo lỗi từ CỦA MÌNH → 400 REPORT_OWN_WORD, không lưu")
  void create_ownWord_isBadRequest() {
    when(wordService.findReadableSense(70L, USER_ID)).thenReturn(ReportFixtures.personalSense(me));

    Throwable ex = catchThrowable(() -> reportService.create(request(70L), USER_ID));

    assertBusiness(ex, HttpStatus.BAD_REQUEST, "REPORT_OWN_WORD");
    verifyNoInteractions(reportRepository);
  }

  @Test
  @DisplayName("Luật 3: đã có báo cáo OPEN cho nghĩa này → 409 REPORT_EXISTS, không lưu")
  void create_alreadyOpen_isConflict() {
    when(wordService.findReadableSense(SENSE_ID, USER_ID)).thenReturn(systemSense);
    when(reportRepository.existsByReporterIdAndSenseIdAndStatus(USER_ID, SENSE_ID, ReportStatus.OPEN)).thenReturn(true);

    Throwable ex = catchThrowable(() -> reportService.create(request(SENSE_ID), USER_ID));

    assertBusiness(ex, HttpStatus.CONFLICT, "REPORT_EXISTS");
    verify(reportRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Luật 3, hai request cùng lúc: unique index chặn ở saveAndFlush → CÙNG mã 409 REPORT_EXISTS")
  void create_raceOnUniqueIndex_isSameConflict() {
    when(wordService.findReadableSense(SENSE_ID, USER_ID)).thenReturn(systemSense);
    when(reportRepository.existsByReporterIdAndSenseIdAndStatus(USER_ID, SENSE_ID, ReportStatus.OPEN)).thenReturn(false);
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
    // Giả lập: request kia vừa INSERT xong giữa lúc existsBy và saveAndFlush.
    when(reportRepository.saveAndFlush(any(ContentReport.class)))
        .thenThrow(new DataIntegrityViolationException("uq_reports_open_per_user_sense"));

    Throwable ex = catchThrowable(() -> reportService.create(request(SENSE_ID), USER_ID));

    assertBusiness(ex, HttpStatus.CONFLICT, "REPORT_EXISTS");
  }

  // ===================== listMine =====================

  @ParameterizedTest(name = "page={0}, size={1} → trang {2}, cỡ {3}")
  @CsvSource({
      "0, 20, 0, 20",     // bình thường
      "-3, 20, 0, 20",    // page âm → 0 (PageRequest.of không nhận số âm)
      "0, 0, 0, 1",       // size 0 → 1
      "1, 999, 1, 50"     // size quá lớn → 50 (bản cũ ném lỗi ở đây vì clamp sai thứ tự)
  })
  @DisplayName("listMine: ép page / size, sắp mới nhất trước, chỉ lấy báo cáo của mình")
  void listMine_clampsAndSorts(int page, int size, int expectedPage, int expectedSize) {
    when(reportRepository.findByReporterId(eq(USER_ID), any(Pageable.class)))
        .thenAnswer(inv -> new PageImpl<ContentReport>(List.of(), inv.getArgument(1), 0));

    reportService.listMine(page, size, USER_ID);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(reportRepository).findByReporterId(eq(USER_ID), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(expectedPage);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(expectedSize);
    assertThat(pageable.getValue().getSort().getOrderFor("createdAt"))
        .isNotNull()
        .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  @DisplayName("listMine: đổi mỗi báo cáo sang ReportResponse qua mapper")
  void listMine_mapsContent() {
    ContentReport report = ReportFixtures.openReport(5L, me, systemSense);
    when(reportRepository.findByReporterId(eq(USER_ID), any(Pageable.class)))
        .thenAnswer(inv -> new PageImpl<>(List.of(report), inv.getArgument(1), 1));

    PageResponse<ReportResponse> page = reportService.listMine(0, 20, USER_ID);

    assertThat(page.content()).singleElement().satisfies(r -> {
      assertThat(r.id()).isEqualTo(5L);
      assertThat(r.term()).isEqualTo("break the ice");
    });
    assertThat(page.totalElements()).isEqualTo(1);
  }

  // ===================== withdraw =====================

  @Test
  @DisplayName("Rút báo cáo OPEN của mình: xóa")
  void withdraw_ownOpen_deletes() {
    ContentReport report = ReportFixtures.openReport(5L, me, systemSense);
    when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

    reportService.withdraw(5L, USER_ID);

    verify(reportRepository).delete(report);
  }

  @Test
  @DisplayName("Luật 4: báo cáo của NGƯỜI KHÁC → 404 REPORT_NOT_FOUND (giống hệt id không tồn tại), không xóa")
  void withdraw_othersReport_isNotFound() {
    ContentReport othersReport = ReportFixtures.openReport(5L, ReportFixtures.user(2L, "Bình"), systemSense);
    when(reportRepository.findById(5L)).thenReturn(Optional.of(othersReport));

    Throwable ex = catchThrowable(() -> reportService.withdraw(5L, USER_ID));

    assertBusiness(ex, HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND");
    verify(reportRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Luật 4: id không tồn tại → 404 REPORT_NOT_FOUND")
  void withdraw_missing_isNotFound() {
    when(reportRepository.findById(anyLong())).thenReturn(Optional.empty());

    Throwable ex = catchThrowable(() -> reportService.withdraw(404L, USER_ID));

    assertBusiness(ex, HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND");
  }

  @Test
  @DisplayName("Luật 5: báo cáo đã được xử lý → 409 REPORT_CLOSED, không xóa (giữ lịch sử)")
  void withdraw_closed_isConflict() {
    ContentReport report = ReportFixtures.openReport(5L, me, systemSense);
    report.resolve(ReportFixtures.admin(), "Đã sửa", NOW);
    when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

    Throwable ex = catchThrowable(() -> reportService.withdraw(5L, USER_ID));

    assertBusiness(ex, HttpStatus.CONFLICT, "REPORT_CLOSED");
    verify(reportRepository, never()).delete(any());
  }
}
