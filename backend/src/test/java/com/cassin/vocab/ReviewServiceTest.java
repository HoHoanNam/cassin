package com.cassin.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.cassin.common.BusinessException;
import com.cassin.common.TimeConfig;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.BoxCount;
import com.cassin.vocab.dto.ReviewCardResponse;
import com.cassin.vocab.dto.ReviewResultResponse;
import com.cassin.vocab.dto.ReviewStatsResponse;

/**
 * Unit test cho ReviewService.
 *
 * Service cần repository (nói chuyện với DB). Unit test không dùng DB, nên thay repository bằng
 * MOCK: một object giả do Mockito tạo, có đủ method nhưng không làm gì cả.
 *   - when(mock.method(...)).thenReturn(x) : "nếu được gọi với tham số này thì trả về x"
 *   - verify(mock).method(...)             : "kiểm tra method này ĐÃ được gọi với tham số này"
 *   - Method chưa dạy (when) thì trả giá trị rỗng: null, 0, false, List rỗng, Optional rỗng.
 *
 * Thứ được test ở đây là LOGIC ĐIỀU PHỐI của service: gọi đúng repository, ghép kết quả đúng thứ tự,
 * đổi lỗi của entity thành đúng BusinessException. Luật Leitner đã có CardProgressTest lo.
 *
 * @ExtendWith(MockitoExtension.class): bật Mockito cho JUnit 5, tự tạo các field @Mock.
 * Nó chạy ở chế độ "strict": dạy when(...) mà test không dùng tới là test bị báo lỗi,
 * giúp test không chứa dòng thừa gây hiểu nhầm.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
  private static final Long USER_ID = 1L;
  private static final Long DECK_ID = 10L;
  private static final Long CARD_ID = 100L;
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

  @Mock CardProgressRepository progressRepository;
  @Mock CardRepository cardRepository;
  @Mock UserRepository userRepository;
  // DeckService là service thật của mình, nhưng ở đây cũng mock: test ReviewService
  // không nên phụ thuộc vào luật quyền của DeckService (đã có chỗ khác lo).
  @Mock DeckService deckService;
  // [THÊM] ReviewService giờ cần thêm StudyDayRepository (đánh dấu ngày học, tính streak).
  @Mock StudyDayRepository studyDayRepository;

  private ReviewService reviewService;

  private final User user = new User("an@gmail.com", "hash", "An");
  private final Deck deck = Deck.system("1000 từ thông dụng", null);

  @BeforeEach
  void setUp() {
    // "Đóng băng" thời gian: với service này, hôm nay LUÔN là TODAY.
    // Đây chính là lý do TimeConfig cung cấp Clock thành bean thay vì gọi LocalDate.now() trực tiếp.
    Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(TimeConfig.APP_ZONE).toInstant(), TimeConfig.APP_ZONE);

    // Tự tạo service bằng constructor (thứ tự tham số = thứ tự field final, do @RequiredArgsConstructor sinh).
    // [SỬA] Thêm studyDayRepository ĐÚNG VỊ TRÍ field trong ReviewService (trước clock).
    // Thiếu hoặc sai thứ tự → test không biên dịch được.
    reviewService = new ReviewService(
        progressRepository, cardRepository, userRepository, deckService, studyDayRepository, fixedClock);
  }

  // ===================== getQueue =====================

  @Test
  @DisplayName("Hàng đợi: thẻ đến hạn đứng trước, thẻ mới đứng sau")
  void getQueue_putsDueCardsFirst_thenNewCards() {
    // Arrange: 1 thẻ đến hạn (tạo 3 ngày trước, chưa ôn) + 1 thẻ mới
    CardProgress dueProgress = CardProgress.start(user, newCard("improve"), TODAY.minusDays(3));
    Card freshCard = newCard("achieve");
    when(progressRepository.findDueInDeck(eq(USER_ID), eq(DECK_ID), eq(TODAY), any(Pageable.class)))
        .thenReturn(List.of(dueProgress));
    when(cardRepository.findNewCards(eq(DECK_ID), eq(USER_ID), any(Pageable.class)))
        .thenReturn(List.of(freshCard));

    // Act
    List<ReviewCardResponse> queue = reviewService.getQueue(DECK_ID, 10, USER_ID);

    // Assert: đúng 2 thẻ, đúng thứ tự. TEST NÀY BẮT ĐƯỢC BUG "bỏ quên kết quả findNewCards".
    assertThat(queue)
        // Dùng lambda r -> r.isNew() thay cho method reference ReviewCardResponse::isNew:
        // chạy y hệt, nhưng IDE không báo warning "Null type safety".
        .extracting(r -> r.card().word(), r -> r.isNew())
        .containsExactly(
            tuple("improve", false),
            tuple("achieve", true));
    // Phải kiểm tra quyền xem bộ trước khi trả thẻ.
    verify(deckService).findReadable(DECK_ID, USER_ID);
  }

  @Test
  @DisplayName("newLimit quá lớn bị ép về tối đa 50")
  void getQueue_clampsNewLimit() {
    // Không cần when(...): mock tự trả List rỗng cho cả findDueInDeck và findNewCards.
    reviewService.getQueue(DECK_ID, 1000, USER_ID);

    // ArgumentCaptor: "bắt" tham số mà service đã truyền vào mock, để kiểm tra.
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(cardRepository).findNewCards(eq(DECK_ID), eq(USER_ID), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  @DisplayName("newLimit = 0: chỉ ôn thẻ cũ, không truy vấn thẻ mới")
  void getQueue_newLimitZero_skipsNewCards() {
    reviewService.getQueue(DECK_ID, 0, USER_ID);

    verify(cardRepository, never()).findNewCards(any(), any(), any());
  }

  @Test
  @DisplayName("Bộ không xem được: lỗi của DeckService đi thẳng ra ngoài, không truy vấn thẻ")
  void getQueue_unreadableDeck_throws() {
    BusinessException notFound = new BusinessException(HttpStatus.NOT_FOUND, "DECK_NOT_FOUND", "Không tìm thấy bộ từ");
    when(deckService.findReadable(DECK_ID, USER_ID)).thenThrow(notFound);

    assertThatThrownBy(() -> reviewService.getQueue(DECK_ID, 10, USER_ID)).isSameAs(notFound);
    verifyNoInteractions(progressRepository, cardRepository);
  }

  // ===================== review =====================

  @Test
  @DisplayName("Ôn lần đầu: tạo tiến độ mới, nhớ → hộp 2, lưu xuống DB")
  void review_firstTime_createsProgress() {
    Card card = newCard("improve");
    when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
    when(progressRepository.findByUserIdAndCardId(USER_ID, CARD_ID)).thenReturn(Optional.empty());
    when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
    // save(x) trả về chính x (giống JPA thật khi lưu entity mới).
    when(progressRepository.save(any(CardProgress.class))).thenAnswer(inv -> inv.getArgument(0));

    ReviewResultResponse result = reviewService.review(CARD_ID, true, USER_ID);

    assertThat(result.box()).isEqualTo(2);
    assertThat(result.dueDate()).isEqualTo(TODAY.plusDays(2));
    assertThat(result.reviewCount()).isEqualTo(1);
    verify(deckService).checkReadable(deck, USER_ID);
    verify(progressRepository).save(any(CardProgress.class));
    // [THÊM] Ôn hợp lệ → hôm nay được tính là "có học" (để tính streak).
    verify(studyDayRepository).markStudied(USER_ID, TODAY);
  }

  @Test
  @DisplayName("Ôn thẻ đã có tiến độ: dùng lại tiến độ cũ, không tạo mới")
  void review_existingProgress_isReused() {
    Card card = newCard("improve");
    CardProgress existing = CardProgress.start(user, card, TODAY.minusDays(1));  // đến hạn từ hôm qua
    when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
    when(progressRepository.findByUserIdAndCardId(USER_ID, CARD_ID)).thenReturn(Optional.of(existing));
    when(progressRepository.save(existing)).thenReturn(existing);

    ReviewResultResponse result = reviewService.review(CARD_ID, false, USER_ID);

    assertThat(result.box()).isEqualTo(1);
    assertThat(result.lapseCount()).isEqualTo(1);
    // Đã có tiến độ thì không cần lấy User để tạo mới.
    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("Ôn 2 lần trong ngày: IllegalStateException của entity thành BusinessException 409")
  void review_sameDay_becomesConflict() {
    Card card = newCard("improve");
    CardProgress progress = CardProgress.start(user, card, TODAY);
    progress.review(true, TODAY);              // đã ôn hôm nay
    when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
    when(progressRepository.findByUserIdAndCardId(USER_ID, CARD_ID)).thenReturn(Optional.of(progress));

    assertThatThrownBy(() -> reviewService.review(CARD_ID, true, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getCode()).isEqualTo("REVIEW_NOT_ALLOWED");
          assertThat(ex.getMessage()).isEqualTo("Thẻ này đã được ôn hôm nay");
        });
    verify(progressRepository, never()).save(any());
    // [THÊM] Lượt ôn bị từ chối thì không được tính là một ngày học.
    verifyNoInteractions(studyDayRepository);
  }

  @Test
  @DisplayName("Thẻ không tồn tại: 404 CARD_NOT_FOUND")
  void review_cardNotFound() {
    when(cardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.review(CARD_ID, true, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getCode()).isEqualTo("CARD_NOT_FOUND");
        });
    verifyNoInteractions(progressRepository);
  }

  @Test
  @DisplayName("Thẻ thuộc bộ của người khác: bị chặn, không lưu tiến độ")
  void review_unreadableDeck_isRejected() {
    Card card = newCard("improve");
    when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
    // checkReadable trả về void → không dùng được when(...).thenThrow, phải dùng doThrow(...).when(...)
    doThrow(new BusinessException(HttpStatus.NOT_FOUND, "DECK_NOT_FOUND", "Không tìm thấy bộ từ"))
        .when(deckService).checkReadable(deck, USER_ID);

    assertThatThrownBy(() -> reviewService.review(CARD_ID, true, USER_ID))
        .isInstanceOf(BusinessException.class);
    verifyNoInteractions(progressRepository, studyDayRepository);
  }

  // ===================== getAllDue (GET /api/review-queue) =====================

  @Test
  @DisplayName("Hàng đợi gộp: chỉ thẻ đến hạn, giữ nguyên thứ tự DB trả về, không lấy thẻ mới")
  void getAllDue_returnsDueCardsInOrder_withoutNewCards() {
    CardProgress older = CardProgress.start(user, newCard("improve"), TODAY.minusDays(5));
    CardProgress newer = CardProgress.start(user, newCard("achieve"), TODAY.minusDays(1));
    when(progressRepository.findAllDue(eq(USER_ID), eq(TODAY), any(Pageable.class)))
        .thenReturn(List.of(older, newer));

    List<ReviewCardResponse> queue = reviewService.getAllDue(USER_ID);

    assertThat(queue)
        .extracting(r -> r.card().word(), r -> r.isNew())
        .containsExactly(
            tuple("improve", false),
            tuple("achieve", false));
    // Hàng đợi gộp không kèm thẻ mới (thẻ mới học theo từng bộ) và không cần kiểm tra 1 bộ cụ thể.
    verifyNoInteractions(cardRepository, deckService);
  }

  @Test
  @DisplayName("Hàng đợi gộp: tối đa 100 thẻ mỗi lượt")
  void getAllDue_limitsTo100() {
    reviewService.getAllDue(USER_ID);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(progressRepository).findAllDue(eq(USER_ID), eq(TODAY), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
  }

  // ===================== getStats =====================

  @Test
  @DisplayName("Thống kê: luôn đủ 5 hộp theo thứ tự, hộp trống = 0")
  void getStats_fillsEmptyBoxesWithZero() {
    // [SỬA] countByUserIdAndDueDateLessThanEqual → countAllDue (đếm cùng điều kiện với hàng đợi).
    when(progressRepository.countAllDue(USER_ID, TODAY)).thenReturn(18L);
    // DB chỉ trả hộp 1 và 3
    when(progressRepository.countByBox(USER_ID)).thenReturn(List.of(new BoxCount(1, 24), new BoxCount(3, 40)));

    ReviewStatsResponse stats = reviewService.getStats(USER_ID);

    assertThat(stats.dueToday()).isEqualTo(18);
    // record tự có equals() so sánh từng field → so sánh thẳng được
    assertThat(stats.boxes()).containsExactly(
        new BoxCount(1, 24),
        new BoxCount(2, 0),
        new BoxCount(3, 40),
        new BoxCount(4, 0),
        new BoxCount(5, 0));
    // recentDays không được dạy → mock trả List rỗng → chưa học ngày nào → streak = 0.
    assertThat(stats.streakDays()).isZero();
  }

  @Test
  @DisplayName("Thống kê: streakDays tính từ các ngày học trong DB")
  void getStats_includesStreak() {
    // Học hôm nay, hôm qua, hôm kia; nghỉ 4 ngày trước → chuỗi 3 ngày.
    when(studyDayRepository.recentDays(eq(USER_ID), any(Pageable.class)))
        .thenReturn(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(4)));

    ReviewStatsResponse stats = reviewService.getStats(USER_ID);

    assertThat(stats.streakDays()).isEqualTo(3);
    // Chỉ lấy tối đa MAX_STREAK_DAYS ngày gần nhất, không tải toàn bộ lịch sử.
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(studyDayRepository).recentDays(eq(USER_ID), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(ReviewService.MAX_STREAK_DAYS);
  }

  // ===================== streak (hàm tĩnh, không cần mock) =====================
  // Hàm static thuần: vào List ngày + hôm nay, ra một con số → test thẳng, không cần service.

  @Test
  @DisplayName("Streak: chưa học ngày nào → 0")
  void streak_noDays_isZero() {
    assertThat(ReviewService.streak(List.of(), TODAY)).isZero();
  }

  @Test
  @DisplayName("Streak: hôm nay đã học → đếm cả hôm nay")
  void streak_studiedToday_countsToday() {
    assertThat(ReviewService.streak(List.of(TODAY, TODAY.minusDays(1)), TODAY)).isEqualTo(2);
  }

  @Test
  @DisplayName("Streak: hôm nay CHƯA học nhưng hôm qua có → chuỗi vẫn còn, đếm từ hôm qua")
  void streak_notYetToday_keepsStreakFromYesterday() {
    List<LocalDate> days = List.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3));

    assertThat(ReviewService.streak(days, TODAY)).isEqualTo(3);
  }

  @Test
  @DisplayName("Streak: bỏ trọn hôm qua → chuỗi mất, về 0")
  void streak_missedYesterday_isZero() {
    // Lần học gần nhất là hôm kia, hôm qua và hôm nay đều không học.
    assertThat(ReviewService.streak(List.of(TODAY.minusDays(2), TODAY.minusDays(3)), TODAY)).isZero();
  }

  @Test
  @DisplayName("Streak: chỉ đếm đoạn liên tiếp gần nhất, dừng ở ngày nghỉ đầu tiên")
  void streak_stopsAtFirstGap() {
    // Học 3 ngày gần nhất, nghỉ 1 ngày, trước đó học 10 ngày liền → chỉ tính 3.
    List<LocalDate> days = new ArrayList<>(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)));
    for (int i = 4; i < 14; i++) {
      days.add(TODAY.minusDays(i));
    }

    assertThat(ReviewService.streak(days, TODAY)).isEqualTo(3);
  }

  @Test
  @DisplayName("Streak: thứ tự ngày truyền vào không ảnh hưởng kết quả")
  void streak_orderDoesNotMatter() {
    List<LocalDate> shuffled = List.of(TODAY.minusDays(2), TODAY, TODAY.minusDays(1));

    assertThat(ReviewService.streak(shuffled, TODAY)).isEqualTo(3);
  }

  @Test
  @DisplayName("Streak: chuỗi đi qua ranh giới tháng/năm vẫn liên tiếp")
  void streak_acrossYearBoundary() {
    LocalDate newYear = LocalDate.of(2027, 1, 1);
    List<LocalDate> days = List.of(newYear, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 30));

    assertThat(ReviewService.streak(days, newYear)).isEqualTo(3);
  }

  // ===================== helper =====================

  private Card newCard(String word) {
    return Card.create(word, PartOfSpeech.VERB, null, "nghĩa của " + word, List.of(), deck);
  }
}
