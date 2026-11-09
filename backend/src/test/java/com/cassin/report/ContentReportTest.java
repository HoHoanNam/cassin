package com.cassin.report;

import static com.cassin.report.ReportFixtures.NOW;
import static com.cassin.report.ReportFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.cassin.user.User;

/**
 * ContentReport giữ luật chuyển trạng thái: OPEN → RESOLVED / REJECTED, đóng rồi không mở lại.
 * Test thuần Java, KHÔNG mock (giống CardProgressTest): entity không phụ thuộc gì ngoài chính nó.
 */
class ContentReportTest {
  private static final Instant LATER = NOW.plusSeconds(3600);

  private final User learner = ReportFixtures.learner();
  private final User admin = ReportFixtures.admin();

  private ContentReport newReport(String message) {
    return ContentReport.open(learner, ReportFixtures.systemSense(), ReportReason.WRONG_DEFINITION, message, NOW);
  }

  // ===================== open =====================

  @Test
  @DisplayName("open: OPEN, chưa có người xử lý / thời điểm xử lý, createdAt = updatedAt = now")
  void open_startsOpen() {
    ContentReport report = newReport("Nghĩa chưa đúng");

    assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
    assertThat(report.isOpen()).isTrue();
    assertThat(report.getReason()).isEqualTo(ReportReason.WRONG_DEFINITION);
    assertThat(report.getMessage()).isEqualTo("Nghĩa chưa đúng");
    assertThat(report.getResolvedBy()).isNull();
    assertThat(report.getResolvedAt()).isNull();   // chk_reports_closed: OPEN ⇔ resolved_at NULL
    assertThat(report.getAdminNote()).isNull();
    assertThat(report.getCreatedAt()).isEqualTo(NOW);
    assertThat(report.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("open: mô tả bị cắt khoảng trắng hai đầu")
  void open_stripsMessage() {
    assertThat(newReport("  Sai chính tả  ").getMessage()).isEqualTo("Sai chính tả");
  }

  @ParameterizedTest(name = "message = \"{0}\" → null")
  @NullSource
  @ValueSource(strings = { "", "   " })
  @DisplayName("open: mô tả rỗng / toàn khoảng trắng → lưu null")
  void open_blankMessage_isNull(String message) {
    assertThat(newReport(message).getMessage()).isNull();
  }

  @Test
  @DisplayName("isReportedBy: đúng người báo → true, người khác → false")
  void isReportedBy() {
    ContentReport report = newReport(null);

    assertThat(report.isReportedBy(USER_ID)).isTrue();
    assertThat(report.isReportedBy(2L)).isFalse();
  }

  // ===================== resolve =====================

  @Test
  @DisplayName("resolve: RESOLVED, ghi người xử lý, ghi chú (đã cắt khoảng trắng), thời điểm xử lý")
  void resolve_closesReport() {
    ContentReport report = newReport(null);

    report.resolve(admin, "  Đã sửa nghĩa  ", LATER);

    assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    assertThat(report.isOpen()).isFalse();
    assertThat(report.getResolvedBy()).isSameAs(admin);
    assertThat(report.getAdminNote()).isEqualTo("Đã sửa nghĩa");
    assertThat(report.getResolvedAt()).isEqualTo(LATER);
    assertThat(report.getUpdatedAt()).isEqualTo(LATER);
    assertThat(report.getCreatedAt()).isEqualTo(NOW);   // không đổi
  }

  @Test
  @DisplayName("resolve: ghi chú KHÔNG bắt buộc (null / rỗng → adminNote null)")
  void resolve_noteIsOptional() {
    ContentReport report = newReport(null);

    report.resolve(admin, "   ", LATER);

    assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    assertThat(report.getAdminNote()).isNull();
  }

  // ===================== reject =====================

  @Test
  @DisplayName("reject có lý do: REJECTED, lưu lý do")
  void reject_withNote() {
    ContentReport report = newReport(null);

    report.reject(admin, "Nghĩa này đúng theo Cambridge", LATER);

    assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
    assertThat(report.getAdminNote()).isEqualTo("Nghĩa này đúng theo Cambridge");
    assertThat(report.getResolvedAt()).isEqualTo(LATER);
  }

  @ParameterizedTest(name = "note = \"{0}\" → IllegalArgumentException")
  @NullSource
  @ValueSource(strings = { "", "   " })
  @DisplayName("reject thiếu lý do: IllegalArgumentException, báo cáo VẪN OPEN (không đổi gì)")
  void reject_withoutNote_throwsAndKeepsOpen(String note) {
    ContentReport report = newReport(null);

    assertThatThrownBy(() -> report.reject(admin, note, LATER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cần ghi lý do từ chối");
    assertThat(report.isOpen()).isTrue();
    assertThat(report.getResolvedAt()).isNull();
  }

  // ===================== đã đóng thì không mở / xử lý lại =====================

  @Test
  @DisplayName("resolve lần 2: IllegalStateException, giữ nguyên kết quả lần đầu")
  void resolveTwice_throws() {
    ContentReport report = newReport(null);
    report.resolve(admin, "Lần đầu", LATER);
    User otherAdmin = ReportFixtures.user(100L, "Admin khác");

    assertThatThrownBy(() -> report.resolve(otherAdmin, "Lần hai", LATER.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Báo cáo này đã được xử lý");
    assertThat(report.getResolvedBy()).isSameAs(admin);
    assertThat(report.getAdminNote()).isEqualTo("Lần đầu");
    assertThat(report.getResolvedAt()).isEqualTo(LATER);
  }

  @Test
  @DisplayName("Đã RESOLVED rồi reject (có lý do): vẫn IllegalStateException")
  void rejectAfterResolve_throws() {
    ContentReport report = newReport(null);
    report.resolve(admin, null, LATER);

    assertThatThrownBy(() -> report.reject(admin, "Đổi ý", LATER))
        .isInstanceOf(IllegalStateException.class);
    assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
  }
}
