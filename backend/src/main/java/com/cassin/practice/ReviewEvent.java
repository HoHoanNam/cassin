package com.cassin.practice;

import java.time.Instant;
import java.util.Objects;

import com.cassin.user.User;
import com.cassin.vocab.Sense;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Nhật ký: MỖI lần trả lời ở chế độ luyện = 1 dòng (bảng review_events, V10).
 * Chỉ thêm, không bao giờ sửa → không cần updated_at.
 * Dùng cho thống kê ("tuần này luyện 120 câu, đúng 85%") và "Sổ lỗi sai" về sau.
 */
@Entity
@Table(name = "review_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  // Hibernate đọc dòng này để tự sinh "ON s.id = e.sense_id"
  @JoinColumn(name = "sense_id", nullable = false)
  private Sense sense;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PracticeMode mode;

  @Column(nullable = false)
  private boolean correct;

  @Column(name = "duration_ms", nullable = false)
  private int durationMs;

  @Column(name = "reviewed_at", nullable = false, updatable = false)
  private Instant reviewedAt = Instant.now();

  public static ReviewEvent of(User user, Sense sense, PracticeMode mode, boolean correct, int durationMs) {
    ReviewEvent event = new ReviewEvent();
    event.user = Objects.requireNonNull(user, "User không được null");
    event.sense = Objects.requireNonNull(sense, "Nghĩa không được null");
    event.mode = Objects.requireNonNull(mode, "Chế độ không được null");
    event.correct = correct;
    // Thời gian âm là dữ liệu hỏng từ client → ghi 0 thay vì để DB từ chối cả lượt luyện.
    event.durationMs = Math.max(durationMs, 0);
    return event;
  }
}
