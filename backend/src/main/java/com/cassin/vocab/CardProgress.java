package com.cassin.vocab;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import com.cassin.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tiến độ ôn của MỘT user với MỘT nghĩa, theo hệ thống hộp Leitner.
 *
 *   Hộp:          1    2    3    4    5
 *   Ôn lại sau:   1    2    4    7    15  (ngày)
 *
 *   Nhớ  → lên 1 hộp (tối đa hộp 5)
 *   Quên → về hộp 1, tăng lapseCount
 *
 * [V10] card → sense: tiến độ gắn với từng nghĩa. Từ "light" có 2 nghĩa = 2 dòng tiến độ độc lập:
 * nhớ "ánh sáng" mà quên "nhẹ" thì chỉ nghĩa "nhẹ" quay về hộp 1.
 *
 * Mọi method nhận "today" làm tham số (không tự gọi LocalDate.now()) để test chọn được ngày bất kỳ.
 */
@Entity
@Table(name = "card_progresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardProgress {
  public static final int MIN_BOX = 1;
  public static final int MAX_BOX = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private int box;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "last_reviewed_on")
  private LocalDate lastReviewedOn;

  @Column(name = "review_count", nullable = false)
  private int reviewCount;

  @Column(name = "lapse_count", nullable = false)
  private int lapseCount;

  // [THÊM] Optimistic locking (V13). Hibernate tự thêm "AND version = ?" vào mỗi UPDATE và tự tăng số này.
  // Hai request cùng sửa một dòng → request ghi sau không khớp version → 409 CONCURRENT_UPDATE.
  // Không setter, không tự gán: chỉ Hibernate được đổi field này.
  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  // [V10] Cột card_id đã được V10 đổi tên thành sense_id.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sense_id", nullable = false)
  private Sense sense;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** Lần đầu học một nghĩa: hộp 1, đến hạn ngay hôm nay. */
  public static CardProgress start(User user, Sense sense, LocalDate today) {
    Objects.requireNonNull(user, "User không được null");
    Objects.requireNonNull(sense, "Nghĩa không được null");
    Objects.requireNonNull(today, "Ngày hôm nay không được null");

    CardProgress progress = new CardProgress();
    progress.user = user;
    progress.sense = sense;
    progress.box = MIN_BOX;
    progress.dueDate = today;
    progress.lastReviewedOn = null;
    progress.reviewCount = 0;
    progress.lapseCount = 0;
    return progress;
  }

  /**
   * Chấm một lần ôn.
   * Ôn 2 lần trong ngày, hoặc ôn khi chưa đến hạn → IllegalStateException (service đổi thành 409).
   */
  public void review(boolean remembered, LocalDate today) {
    Objects.requireNonNull(today, "Ngày hôm nay không được null");
    if (today.equals(lastReviewedOn)) {
      throw new IllegalStateException("Thẻ này đã được ôn hôm nay");
    }
    if (!isDue(today)) {
      throw new IllegalStateException("Thẻ này chưa đến hạn ôn");
    }
    if (remembered) {
      box = Math.min(box + 1, MAX_BOX);
    } else {
      box = MIN_BOX;
      lapseCount++;
    }
    dueDate = today.plusDays(intervalDays(box));
    lastReviewedOn = today;
    reviewCount++;
  }

  /**
   * [V10] Luyện tập (Fill in blank, Sentence builder) làm SAI → đưa nghĩa này về hạn ôn ngày mai.
   *
   *   - KHÔNG đổi hộp, không tăng lapseCount: chỉ lần ôn chính thức (review) mới chấm Leitner.
   *   - Luyện ĐÚNG thì không gọi method này: luyện chế độ dễ không được "thăng hộp" hộ.
   *   - Hạn hiện tại đã sớm hơn (hôm nay / ngày mai) thì giữ nguyên, không đẩy lùi.
   */
  public void dueSoon(LocalDate today) {
    Objects.requireNonNull(today, "Ngày hôm nay không được null");
    LocalDate tomorrow = today.plusDays(1);
    if (dueDate.isAfter(tomorrow)) {
      dueDate = tomorrow;
    }
  }

  public boolean isDue(LocalDate today) {
    return !dueDate.isAfter(today);
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  private static int intervalDays(int box) {
    return switch (box) {
      case 1 -> 1;
      case 2 -> 2;
      case 3 -> 4;
      case 4 -> 7;
      case 5 -> 15;
      default -> throw new IllegalArgumentException("Hộp không hợp lệ: " + box);
    };
  }
}
