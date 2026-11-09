package com.cassin.report;

import java.time.Instant;

import com.cassin.user.User;
import com.cassin.vocab.Sense;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Báo lỗi nội dung: một học viên báo một NGHĨA của từ hệ thống bị sai.
 *
 *   OPEN ──admin resolve──▶ RESOLVED
 *     │  └──admin reject───▶ REJECTED (bắt buộc có lý do)
 *     └──học viên rút──▶ (dòng bị xóa)
 *
 * Đã đóng thì không mở lại; muốn báo tiếp thì tạo báo cáo mới.
 * Luật chuyển trạng thái nằm ở đây (giống CardProgress), service chỉ gọi và đổi lỗi sang HTTP.
 */
@Entity
@Table(name = "content_reports")
@Getter
// [SỬA] @NoArgsConstructor → access = PROTECTED.
// JPA cần constructor rỗng để dựng object khi đọc DB, nhưng code bên ngoài không được tự "new ContentReport()"
// rồi bỏ qua open() (sẽ ra báo cáo thiếu reporter / sense). Cùng cách với Deck, Word, CardProgress.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentReport {
  public static final int MAX_TEXT = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reporter_id", nullable = false)
  private User reporter;

  // [SỬA] @Column → @JoinColumn(name = "sense_id").
  // Quan hệ (@ManyToOne) là KHÓA NGOẠI, khai báo bằng @JoinColumn. Đặt @Column lên quan hệ thì Hibernate
  // báo AnnotationException ngay lúc khởi động → app không chạy được.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sense_id", nullable = false)
  private Sense sense;

  // [SỬA] Thêm length khớp VARCHAR(30) / VARCHAR(20) trong V12 (đọc entity là biết giới hạn cột).
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReportReason reason;

  @Column(length = MAX_TEXT)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReportStatus status = ReportStatus.OPEN;

  // [SỬA] Bỏ nullable = false: báo cáo đang OPEN chưa có ghi chú của admin → cột này NULL.
  // V12 cũng để admin_note cho phép NULL; entity phải nói đúng như DB.
  @Column(name = "admin_note", length = MAX_TEXT)
  private String adminNote;

  // [SỬA] Bỏ @Column, sửa tên cột "resolved_by_id" → "resolved_by" (đúng tên trong V12).
  // Hai lỗi cũ: @Column trên quan hệ (như sense ở trên) và tên cột không tồn tại
  // (ddl-auto = validate sẽ báo "missing column [resolved_by_id]").
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resolved_by")
  private User resolvedBy;

  // [SỬA] Cột "created_at" → "resolved_at", bỏ nullable = false và updatable = false.
  // Bản cũ map resolvedAt VÀ createdAt cùng vào cột created_at → Hibernate báo
  // "Column 'created_at' is duplicated in mapping". resolvedAt NULL khi còn OPEN, và phải UPDATE được lúc đóng.
  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Package-private: chỉ ReportService (cùng package) tạo báo cáo. */
  static ContentReport open(User reporter, Sense sense, ReportReason reason, String message, Instant now) {
    ContentReport report = new ContentReport();
    report.reporter = reporter;
    report.sense = sense;
    report.reason = reason;
    // [SỬA] "   " hoặc "" → null: không lưu chuỗi toàn khoảng trắng vào DB.
    report.message = blankToNull(message);
    report.status = ReportStatus.OPEN;
    report.createdAt = now;
    report.updatedAt = now;
    return report;
  }

  public void resolve(User admin, String note, Instant now) {
    close(ReportStatus.RESOLVED, admin, note, now);
  }

  public void reject(User admin, String note, Instant now) {
    // [SỬA] note.isEmpty() → blankToNull(note) == null.
    //   - note = null      → bản cũ ném NullPointerException (thành lỗi 500)
    //   - note = "   "     → bản cũ cho qua, lưu lý do rỗng
    if (blankToNull(note) == null) {
      throw new IllegalArgumentException("Cần ghi lý do từ chối");
    }
    close(ReportStatus.REJECTED, admin, note, now);
  }

  private void close(ReportStatus target, User admin, String note, Instant now) {
    if (status != ReportStatus.OPEN) {
      throw new IllegalStateException("Báo cáo này đã được xử lý");
    }
    this.status = target;
    this.adminNote = blankToNull(note); // [SỬA] cắt khoảng trắng, rỗng → null
    this.resolvedBy = admin;
    this.resolvedAt = now;  // chk_reports_closed trong V12: đã đóng ⇔ resolved_at có giá trị
    this.updatedAt = now;
  }

  public boolean isOpen() {
    return status == ReportStatus.OPEN;
  }

  public boolean isReportedBy(Long userId) {
    return reporter.getId().equals(userId); // getId() trên proxy LAZY không chạy SELECT
  }

  // [THÊM]
  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }
}
