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
 * Tiến độ ôn một thẻ của MỘT người dùng theo phương pháp hộp Leitner.
 *
 * Mỗi cặp (user, card) có tối đa một dòng (unique trong V5).
 * Thẻ chưa ôn lần nào thì CHƯA có dòng: service gọi start(...) ngay trước lần review đầu tiên.
 *
 * Mọi method nhận "today" làm tham số, KHÔNG tự gọi LocalDate.now():
 * - test được: truyền một ngày cố định, kết quả không đổi theo ngày chạy test;
 * - đúng múi giờ: service tính today theo Asia/Ho_Chi_Minh rồi truyền xuống,
 *   không phụ thuộc múi giờ của máy chủ.
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

  // [SỬA] Bỏ columnDefinition = "INT CHECK (...)".
  // columnDefinition chỉ có tác dụng khi Hibernate TỰ TẠO bảng. Ở đây Flyway tạo bảng
  // (ddl-auto: validate), nên dòng đó không chặn được gì, chỉ gây hiểu nhầm.
  // CHECK thật nằm trong V5; trong Java, review(...) không bao giờ đưa box ra ngoài 1..5.
  @Column(nullable = false)
  private int box;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  // Null = chưa ôn lần nào.
  @Column(name = "last_reviewed_on")
  private LocalDate lastReviewedOn;

  @Column(name = "review_count", nullable = false)
  private int reviewCount;

  // lapseCount: số lần quên
  @Column(name = "lapse_count", nullable = false)
  private int lapseCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  // Owning side: mỗi card có N card_progress (theo N user)
  // [SỬA] Thêm optional = false + nullable = false: tiến độ luôn phải gắn với một thẻ.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "card_id", nullable = false)
  private Card card;

  // Owning side: mỗi user có N card_progress (theo N card)
  // [SỬA] Tương tự: tiến độ luôn phải thuộc về một người dùng.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /**
   * Tạo tiến độ cho một thẻ người dùng chưa ôn lần nào.
   *
   * [SỬA] Thêm "static": factory method phải gọi được khi CHƯA có object nào,
   * CardProgress.start(...). Không có static thì phải có sẵn một CardProgress mới gọi được.
   * [SỬA] Nhận "today" làm tham số thay vì LocalDate.now() (xem giải thích ở đầu class).
   */
  public static CardProgress start(User user, Card card, LocalDate today) {
    Objects.requireNonNull(user, "User không được null");
    Objects.requireNonNull(card, "Card không được null");
    Objects.requireNonNull(today, "Ngày hôm nay không được null");

    CardProgress progress = new CardProgress();
    progress.user = user;
    progress.card = card;
    progress.box = MIN_BOX;
    // [SỬA] Thẻ mới đến hạn NGAY HÔM NAY (rule 2 trong kế hoạch), không phải ngày mai.
    progress.dueDate = today;
    // [SỬA] Chưa ôn lần nào → null. Trước đây gán = today, nên lần review ĐẦU TIÊN
    // sẽ bị chặn bởi rule "đã chấm trong hôm nay" → thẻ mới không bao giờ ôn được trong ngày tạo.
    progress.lastReviewedOn = null;
    progress.reviewCount = 0;
    progress.lapseCount = 0;
    return progress;
  }

  /**
   * [THÊM] Chấm một lần ôn: remembered = true là "Nhớ", false là "Quên".
   *
   * Ném IllegalStateException (lỗi do TRẠNG THÁI hiện tại không cho phép, không phải do tham số sai).
   * Service sẽ bắt và trả lỗi cho người dùng.
   */
  public void review(boolean remembered, LocalDate today) {
    Objects.requireNonNull(today, "Ngày hôm nay không được null");

    // Rule 6: một thẻ chỉ chấm một lần mỗi ngày (chống bấm "Nhớ" liên tục để "cày" hộp).
    if (today.equals(lastReviewedOn)) {
      throw new IllegalStateException("Thẻ này đã được ôn hôm nay");
    }

    // Quyết định thiết kế: CHẶN ôn thẻ chưa đến hạn, đúng tinh thần Leitner.
    // Nếu cho phép, người dùng ôn trước hạn mỗi ngày là đẩy thẻ lên hộp 5 chỉ sau 4 ngày.
    // Muốn cho phép thì xóa khối if này.
    if (!isDue(today)) {
      throw new IllegalStateException("Thẻ này chưa đến hạn ôn");
    }

    // Rule 4: Nhớ → lên hộp kế tiếp, tối đa hộp 5. Quên → về hộp 1.
    // Math.min giữ box không vượt quá 5: đang ở hộp 5 mà nhớ thì vẫn ở hộp 5.
    if (remembered) {
      box = Math.min(box + 1, MAX_BOX);
    } else {
      box = MIN_BOX;
      lapseCount++;
    }

    // Rule 5: hạn ôn tiếp theo = hôm nay + số ngày của hộp MỚI (box đã cập nhật ở trên).
    dueDate = today.plusDays(intervalDays(box));
    lastReviewedOn = today;
    reviewCount++;
  }

  /** [THÊM] Đã đến hạn ôn chưa? Hạn hôm nay hoặc đã quá hạn đều tính là đến hạn. */
  public boolean isDue(LocalDate today) {
    return !dueDate.isAfter(today);
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  /**
   * [THÊM] Bảng số ngày theo hộp. Dùng switch expression của Java 21:
   * mỗi nhánh trả về một giá trị, thiếu nhánh hay giá trị lạ thì rơi vào default và ném lỗi.
   */
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
