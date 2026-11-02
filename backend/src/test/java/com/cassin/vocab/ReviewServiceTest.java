package com.cassin.vocab;

import static com.cassin.vocab.VocabFixtures.sense;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
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
 * [V10] ReviewService: đơn vị ôn là NGHĨA; deckId null = "ôn tất cả" (cả sổ từ).
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
  private static final Long USER_ID = 1L;
  private static final Long DECK_ID = 10L;
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

  @Mock CardProgressRepository progressRepository;
  @Mock SenseRepository senseRepository;
  @Mock UserRepository userRepository;
  @Mock DeckService deckService;
  @Mock WordService wordService;
  @Mock StudyDayRepository studyDayRepository;

  private ReviewService reviewService;

  private final User me = VocabFixtures.user(USER_ID, "an@gmail.com");

  // "light": nghĩa 30 (danh từ) và 31 (tính từ).
  private final Word light = VocabFixtures.word(3L, "light", null,
      sense(PartOfSpeech.NOUN, "ánh sáng", "Turn on the [light]."),
      sense(PartOfSpeech.ADJECTIVE, "nhẹ", "This bag is very [light]."));
  private final Sense lightNoun = light.getSenses().get(0);
  private final Sense lightAdj = light.getSenses().get(1);

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(TimeConfig.APP_ZONE).toInstant(), TimeConfig.APP_ZONE);
    reviewService = new ReviewService(progressRepository, senseRepository, userRepository,
        deckService, wordService, studyDayRepository, fixedClock);
  }

  // ===================== getQueue =====================

  @Test
  @DisplayName("Ôn một bộ: kiểm tra quyền bộ, thẻ đến hạn trước rồi đến thẻ mới")
  void queue_deck_dueFirstThenNew() {
    CardProgress dueProgress = CardProgress.start(me, lightNoun, TODAY);
    when(progressRepository.findDueInDeck(eq(USER_ID), eq(DECK_ID), eq(TODAY), any(PageRequest.class)))
        .thenReturn(List.of(dueProgress));
    when(senseRepository.findNewInDeck(USER_ID, DECK_ID, PageRequest.of(0, 10))).thenReturn(List.of(lightAdj));

    List<ReviewCardResponse> queue = reviewService.getQueue(DECK_ID, 10, USER_ID);

    verify(deckService).findReadable(DECK_ID, USER_ID);
    assertThat(queue).extracting(c -> c.senseId(), c -> c.isNew())
        .containsExactly(tuple(30L, false), tuple(31L, true));
    verify(progressRepository, never()).findAllDue(anyLong(), any(), any());
  }

  @Test
  @DisplayName("Ôn tất cả (deckId null): dùng truy vấn sổ từ, không đụng tới bộ")
  void queue_notebook_usesNotebookQueries() {
    when(progressRepository.findAllDue(eq(USER_ID), eq(TODAY), any(PageRequest.class))).thenReturn(List.of());
    when(senseRepository.findNewInNotebook(USER_ID, PageRequest.of(0, 5))).thenReturn(List.of(lightNoun));

    List<ReviewCardResponse> queue = reviewService.getQueue(null, 5, USER_ID);

    assertThat(queue).singleElement().satisfies(card -> {
      assertThat(card.term()).isEqualTo("light");
      assertThat(card.isNew()).isTrue();
      assertThat(card.box()).isEqualTo(CardProgress.MIN_BOX);
      // Mặt sau hiện NGHĨA KHÁC của cùng từ, không lặp lại chính nó.
      assertThat(card.otherSenses()).extracting(o -> o.definition()).containsExactly("nhẹ");
      // Câu ví dụ đã bỏ dấu [ ].
      assertThat(card.examples()).containsExactly("Turn on the light.");
    });
    verifyNoInteractions(deckService);
  }

  @Test
  @DisplayName("newLimit = 0: chỉ ôn thẻ cũ, không truy vấn thẻ mới")
  void queue_zeroNewLimit_skipsNewQuery() {
    when(progressRepository.findAllDue(eq(USER_ID), eq(TODAY), any(PageRequest.class))).thenReturn(List.of());

    assertThat(reviewService.getQueue(null, 0, USER_ID)).isEmpty();
    verifyNoInteractions(senseRepository);
  }

  @Test
  @DisplayName("newLimit quá lớn bị ép về 50")
  void queue_newLimitClamped() {
    when(progressRepository.findAllDue(eq(USER_ID), eq(TODAY), any(PageRequest.class))).thenReturn(List.of());
    when(senseRepository.findNewInNotebook(USER_ID, PageRequest.of(0, 50))).thenReturn(List.of());

    reviewService.getQueue(null, 999, USER_ID);

    verify(senseRepository).findNewInNotebook(USER_ID, PageRequest.of(0, 50));
  }

  @Test
  @DisplayName("Bộ của người khác: 404 từ deckService, không truy vấn hàng đợi")
  void queue_unreadableDeck_propagates404() {
    when(deckService.findReadable(DECK_ID, USER_ID)).thenThrow(DeckService.deckNotFound());

    assertStatus(() -> reviewService.getQueue(DECK_ID, 10, USER_ID), HttpStatus.NOT_FOUND, "DECK_NOT_FOUND");
    verifyNoInteractions(progressRepository, senseRepository);
  }

  // ===================== review =====================

  @Test
  @DisplayName("Lần ôn đầu: tạo tiến độ mới, lên hộp 2, đánh dấu hôm nay có học")
  void review_firstTime_createsProgress() {
    when(wordService.findReadableSense(31L, USER_ID)).thenReturn(lightAdj);
    when(progressRepository.findByUserIdAndSenseId(USER_ID, 31L)).thenReturn(Optional.empty());
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
    when(progressRepository.save(any(CardProgress.class))).thenAnswer(inv -> inv.getArgument(0));

    ReviewResultResponse result = reviewService.review(31L, true, USER_ID);

    assertThat(result.senseId()).isEqualTo(31L);
    assertThat(result.box()).isEqualTo(2);
    assertThat(result.dueDate()).isAfter(TODAY);
    verify(studyDayRepository).markStudied(USER_ID, TODAY);
  }

  @Test
  @DisplayName("Ôn 2 lần trong ngày: 409 REVIEW_NOT_ALLOWED, không lưu, không đánh dấu ngày học")
  void review_twiceToday_isConflict() {
    CardProgress progress = CardProgress.start(me, lightNoun, TODAY);
    progress.review(false, TODAY);   // đã ôn hôm nay
    when(wordService.findReadableSense(30L, USER_ID)).thenReturn(lightNoun);
    when(progressRepository.findByUserIdAndSenseId(USER_ID, 30L)).thenReturn(Optional.of(progress));

    assertStatus(() -> reviewService.review(30L, true, USER_ID), HttpStatus.CONFLICT, "REVIEW_NOT_ALLOWED");
    verify(progressRepository, never()).save(any());
    verifyNoInteractions(studyDayRepository);
  }

  @Test
  @DisplayName("Nghĩa không đọc được: 404 SENSE_NOT_FOUND từ wordService")
  void review_unreadableSense_isNotFound() {
    when(wordService.findReadableSense(99L, USER_ID))
        .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "SENSE_NOT_FOUND", "Không tìm thấy nghĩa"));

    assertStatus(() -> reviewService.review(99L, true, USER_ID), HttpStatus.NOT_FOUND, "SENSE_NOT_FOUND");
    verifyNoInteractions(progressRepository, studyDayRepository);
  }

  // ===================== getStats / streak =====================

  @Test
  @DisplayName("Thống kê: đủ 5 hộp (hộp trống = 0) + số đến hạn + chuỗi ngày")
  void stats_fillsEmptyBoxes() {
    when(progressRepository.countAllDue(USER_ID, TODAY)).thenReturn(3L);
    when(progressRepository.countByBox(USER_ID)).thenReturn(List.of(new BoxCount(1, 4L), new BoxCount(3, 2L)));
    when(studyDayRepository.recentDays(USER_ID, PageRequest.of(0, ReviewService.MAX_STREAK_DAYS)))
        .thenReturn(List.of(TODAY, TODAY.minusDays(1)));

    ReviewStatsResponse stats = reviewService.getStats(USER_ID);

    assertThat(stats.dueToday()).isEqualTo(3L);
    assertThat(stats.boxes()).extracting(b -> b.total()).containsExactly(4L, 0L, 2L, 0L, 0L);
    assertThat(stats.streakDays()).isEqualTo(2);
  }

  @Test
  @DisplayName("Chuỗi ngày: hôm nay chưa học vẫn tính từ hôm qua; bỏ trọn 1 ngày thì đứt")
  void streak_rules() {
    assertThat(ReviewService.streak(List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY)).isEqualTo(2);
    assertThat(ReviewService.streak(List.of(TODAY.minusDays(2)), TODAY)).isZero();
    assertThat(ReviewService.streak(List.of(), TODAY)).isZero();
  }

  // ===================== helper =====================

  private static void assertStatus(Runnable action, HttpStatus status, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(status);
          assertThat(ex.getCode()).isEqualTo(code);
        });
  }
}
