package com.cassin.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cassin.user.User;

/**
 * Unit test cho luật Leitner trong CardProgress.
 *
 * "Unit test" = test MỘT class, tách khỏi mọi thứ khác: không Spring, không database, không mạng.
 * Chỉ tạo object bằng new / factory method rồi gọi method → chạy trong vài mili giây.
 * Làm được như vậy là nhờ thiết kế của CardProgress: nhận "today" làm tham số thay vì tự gọi
 * LocalDate.now(), nên test chọn được ngày bất kỳ, chạy hôm nay hay năm sau kết quả vẫn y hệt.
 *
 * Mỗi test theo khuôn 3 bước (Arrange - Act - Assert):
 *   1. Chuẩn bị dữ liệu   2. Gọi method cần test   3. Kiểm tra kết quả
 *
 * Chạy: mvn test   (hoặc bấm nút ▶ cạnh tên class / tên method trong IDE)
 */
class CardProgressTest {

  // Một ngày cố định, không dùng LocalDate.now(): test phải cho kết quả giống nhau mọi lúc.
  private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 2);

  // Dùng object thật thay vì mock: User, Deck, Card đều tạo được bằng constructor / factory method,
  // và CardProgress chỉ cần chúng khác null. Không lưu DB nên id của chúng là null, không sao.
  private final User user = new User("an@gmail.com", "hash", "An");
  private final Deck deck = Deck.system("1000 từ thông dụng", null);
  private final Card card = Card.create("improve", PartOfSpeech.VERB, null, "cải thiện", List.of(), deck);

  // ===================== start(...) =====================

  @Test
  @DisplayName("Thẻ mới: hộp 1, đến hạn ngay hôm nay, chưa ôn lần nào")
  void start_newCard_box1_dueToday() {
    CardProgress p = CardProgress.start(user, card, DAY_1);

    assertThat(p.getBox()).isEqualTo(CardProgress.MIN_BOX);
    assertThat(p.getDueDate()).isEqualTo(DAY_1);
    assertThat(p.isDue(DAY_1)).isTrue();
    assertThat(p.getLastReviewedOn()).isNull();
    assertThat(p.getReviewCount()).isZero();
    assertThat(p.getLapseCount()).isZero();
  }

  @Test
  @DisplayName("start() chặn tham số null (fail fast)")
  void start_rejectsNull() {
    assertThatThrownBy(() -> CardProgress.start(null, card, DAY_1)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> CardProgress.start(user, null, DAY_1)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> CardProgress.start(user, card, null)).isInstanceOf(NullPointerException.class);
  }

  // ===================== review(true): Nhớ =====================

  @Test
  @DisplayName("Thẻ mới ôn được ngay trong ngày tạo (bug cũ: lastReviewedOn = today chặn lần ôn đầu)")
  void review_newCard_canBeReviewedOnFirstDay() {
    CardProgress p = CardProgress.start(user, card, DAY_1);

    p.review(true, DAY_1);

    assertThat(p.getBox()).isEqualTo(2);
    assertThat(p.getLastReviewedOn()).isEqualTo(DAY_1);
    assertThat(p.getReviewCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Nhớ liên tục: đi đúng bảng hộp → số ngày (2, 4, 7, 15), dừng ở hộp 5")
  void review_rememberedEveryTime_followsIntervalTable() {
    CardProgress p = CardProgress.start(user, card, DAY_1);

    // Mỗi dòng: { hộp sau khi nhớ, số ngày tới lần ôn sau }
    int[][] expected = {
        {2, 2},
        {3, 4},
        {4, 7},
        {5, 15},
        {5, 15},   // đã ở hộp 5 mà nhớ tiếp: vẫn hộp 5, không vượt MAX_BOX
    };

    LocalDate day = DAY_1;
    for (int[] step : expected) {
      p.review(true, day);

      // Thông báo trong as(...) hiện ra khi test hỏng, cho biết hỏng ở bước nào.
      assertThat(p.getBox()).as("hộp sau khi ôn ngày %s", day).isEqualTo(step[0]);
      assertThat(p.getDueDate()).as("hạn ôn sau khi ôn ngày %s", day).isEqualTo(day.plusDays(step[1]));

      day = p.getDueDate();   // lần ôn sau đúng vào ngày đến hạn
    }
    assertThat(p.getReviewCount()).isEqualTo(expected.length);
    assertThat(p.getLapseCount()).isZero();
  }

  // ===================== review(false): Quên =====================

  @Test
  @DisplayName("Quên: về hộp 1, tăng số lần quên, ôn lại vào ngày mai")
  void review_forgotten_backToBox1() {
    CardProgress p = CardProgress.start(user, card, DAY_1);
    p.review(true, DAY_1);                     // hộp 2, hạn DAY_1 + 2
    LocalDate day3 = DAY_1.plusDays(2);
    p.review(true, day3);                      // hộp 3, hạn day3 + 4
    LocalDate day7 = day3.plusDays(4);

    p.review(false, day7);                     // quên

    assertThat(p.getBox()).isEqualTo(CardProgress.MIN_BOX);
    assertThat(p.getLapseCount()).isEqualTo(1);
    assertThat(p.getDueDate()).isEqualTo(day7.plusDays(1));
    assertThat(p.getReviewCount()).isEqualTo(3);  // quên vẫn tính là một lần ôn
  }

  @Test
  @DisplayName("Quên ngay từ hộp 1: vẫn ở hộp 1 (không xuống dưới MIN_BOX)")
  void review_forgottenInBox1_staysInBox1() {
    CardProgress p = CardProgress.start(user, card, DAY_1);

    p.review(false, DAY_1);

    assertThat(p.getBox()).isEqualTo(1);
    assertThat(p.getDueDate()).isEqualTo(DAY_1.plusDays(1));
  }

  // ===================== Các trường hợp bị chặn =====================

  @Test
  @DisplayName("Ôn 2 lần trong một ngày: bị chặn, và trạng thái KHÔNG đổi")
  void review_twiceSameDay_isRejected_andStateUnchanged() {
    CardProgress p = CardProgress.start(user, card, DAY_1);
    p.review(true, DAY_1);                     // hộp 2

    assertThatThrownBy(() -> p.review(true, DAY_1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Thẻ này đã được ôn hôm nay");

    // Quan trọng không kém việc ném lỗi: lần bị từ chối không được làm thay đổi gì.
    // Nếu kiểm tra nằm SAU dòng box++ thì thẻ vẫn bị đẩy lên hộp 3 dù có báo lỗi.
    assertThat(p.getBox()).isEqualTo(2);
    assertThat(p.getReviewCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("Ôn trước hạn: bị chặn")
  void review_beforeDueDate_isRejected() {
    CardProgress p = CardProgress.start(user, card, DAY_1);
    p.review(true, DAY_1);                     // hạn mới: DAY_1 + 2

    assertThatThrownBy(() -> p.review(true, DAY_1.plusDays(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Thẻ này chưa đến hạn ôn");
    assertThat(p.getBox()).isEqualTo(2);
  }

  @Test
  @DisplayName("Ôn trễ hạn vẫn được; hạn mới tính từ ngày ôn thật, không phải từ hạn cũ")
  void review_overdue_isAllowed_andIntervalCountsFromToday() {
    CardProgress p = CardProgress.start(user, card, DAY_1);
    p.review(true, DAY_1);                     // hạn DAY_1 + 2, nhưng người học bỏ 10 ngày
    LocalDate late = DAY_1.plusDays(12);

    p.review(true, late);

    assertThat(p.getBox()).isEqualTo(3);
    assertThat(p.getDueDate()).isEqualTo(late.plusDays(4));
  }

  @Test
  @DisplayName("review() chặn today = null")
  void review_rejectsNullToday() {
    CardProgress p = CardProgress.start(user, card, DAY_1);
    assertThatThrownBy(() -> p.review(true, null)).isInstanceOf(NullPointerException.class);
  }

  // ===================== isDue(...) =====================

  @Test
  @DisplayName("isDue: trước hạn = false, đúng hạn = true, quá hạn = true")
  void isDue_boundaries() {
    CardProgress p = CardProgress.start(user, card, DAY_1);
    p.review(true, DAY_1);                     // hạn DAY_1 + 2
    LocalDate due = DAY_1.plusDays(2);

    assertThat(p.isDue(due.minusDays(1))).isFalse();
    assertThat(p.isDue(due)).isTrue();
    assertThat(p.isDue(due.plusDays(30))).isTrue();
  }
}
