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

  private ReviewService reviewService;

  private final User user = new User("an@gmail.com", "hash", "An");
  private final Deck deck = Deck.system("1000 từ thông dụng", null);

  @BeforeEach
  void setUp() {
    // "Đóng băng" thời gian: với service này, hôm nay LUÔN là TODAY.
    // Đây chính là lý do TimeConfig cung cấp Clock thành bean thay vì gọi LocalDate.now() trực tiếp.
    Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(TimeConfig.APP_ZONE).toInstant(), TimeConfig.APP_ZONE);

    // Tự tạo service bằng constructor (thứ tự tham số = thứ tự field final, do @RequiredArgsConstructor sinh).
    reviewService = new ReviewService(progressRepository, cardRepository, userRepository, deckService, fixedClock);
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
        .extracting(r -> r.card().word(), ReviewCardResponse::isNew)
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
    verifyNoInteractions(progressRepository);
  }

  // ===================== getStats =====================

  @Test
  @DisplayName("Thống kê: luôn đủ 5 hộp theo thứ tự, hộp trống = 0")
  void getStats_fillsEmptyBoxesWithZero() {
    when(progressRepository.countByUserIdAndDueDateLessThanEqual(USER_ID, TODAY)).thenReturn(18L);
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
  }

  // ===================== helper =====================

  private Card newCard(String word) {
    return Card.create(word, PartOfSpeech.VERB, null, "nghĩa của " + word, List.of(), deck);
  }
}
