package com.cassin.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.common.BusinessException;
import com.cassin.common.TimeConfig;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;
import com.cassin.vocab.dto.DeckSummary;

/**
 * [THÊM] Unit test cho DeckService: 3 con số cardCount / dueCount / newCount và luật quyền.
 *
 * Câu SQL thật (findVisibleSummaries, countDueInDeck...) KHÔNG được kiểm tra ở đây: repository là mock.
 * Muốn kiểm tra SQL cần test tích hợp với PostgreSQL thật (Testcontainers), làm ở bước sau.
 * Ở đây kiểm tra: service gọi đúng repository, đúng tham số (đúng "hôm nay", đúng thứ tự deckId/userId),
 * và ghép kết quả vào DeckResponse đúng chỗ.
 */
@ExtendWith(MockitoExtension.class)
class DeckServiceTest {
  private static final Long USER_ID = 1L;
  private static final Long OTHER_USER_ID = 2L;
  private static final Long DECK_ID = 10L;
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

  @Mock DeckRepository deckRepository;
  @Mock CardRepository cardRepository;
  @Mock UserRepository userRepository;
  @Mock CardProgressRepository progressRepository;

  private DeckService deckService;

  private final User me = userWithId(USER_ID, "an@gmail.com");
  private final User other = userWithId(OTHER_USER_ID, "binh@gmail.com");

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(TimeConfig.APP_ZONE).toInstant(), TimeConfig.APP_ZONE);
    // Thứ tự = thứ tự field final trong DeckService.
    deckService = new DeckService(deckRepository, cardRepository, userRepository, progressRepository, fixedClock);
  }

  // ===================== listDecks =====================

  @Test
  @DisplayName("Danh sách bộ: truyền đúng 'hôm nay' theo Clock, chép đủ 3 con số + updatedAt")
  void listDecks_mapsSummaries() {
    Instant updated = Instant.parse("2026-11-01T03:00:00Z");
    // Thứ tự tham số = thứ tự trong "select new DeckSummary(...)" của DeckRepository.
    DeckSummary system = new DeckSummary(5L, "1000 từ", null, DeckType.SYSTEM_DECK, null, 30L, 4L, 20L, updated);
    DeckSummary mine = new DeckSummary(7L, "Bộ của tôi", "IELTS", DeckType.USER_DECK, USER_ID, 0L, 0L, 0L, updated);
    when(deckRepository.findVisibleSummaries(USER_ID, TODAY)).thenReturn(List.of(system, mine));

    List<DeckResponse> decks = deckService.listDecks(USER_ID);

    assertThat(decks)
        .extracting(d -> d.id(), d -> d.cardCount(), d -> d.dueCount(), d -> d.newCount(), d -> d.updatedAt())
        .containsExactly(
            tuple(5L, 30L, 4L, 20L, updated),
            tuple(7L, 0L, 0L, 0L, updated));
    // Danh sách lấy hết bằng MỘT câu SQL: không được đếm lẻ từng bộ (N+1).
    verifyNoInteractions(cardRepository, progressRepository);
  }

  // ===================== getDeck =====================

  @Test
  @DisplayName("Chi tiết bộ: 3 con số lấy từ đúng 3 câu đếm, đúng deckId / userId / hôm nay")
  void getDeck_countsCardsDueAndNew() {
    Deck deck = withId(Deck.system("1000 từ", null), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));
    when(cardRepository.countByDeckId(DECK_ID)).thenReturn(30L);
    // Nếu service truyền ngược (userId, deckId) thì stub này không khớp → mock trả 0 → test fail.
    when(progressRepository.countDueInDeck(DECK_ID, USER_ID, TODAY)).thenReturn(4L);
    when(progressRepository.countNewInDeck(DECK_ID, USER_ID)).thenReturn(20L);

    DeckResponse response = deckService.getDeck(DECK_ID, USER_ID);

    assertThat(response.cardCount()).isEqualTo(30L);
    assertThat(response.dueCount()).isEqualTo(4L);
    assertThat(response.newCount()).isEqualTo(20L);
  }

  @Test
  @DisplayName("Chi tiết bộ của người khác: 404, không chạy câu đếm nào")
  void getDeck_othersDeck_isNotFound() {
    Deck othersDeck = withId(Deck.personal("Bộ của Bình", null, other), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(othersDeck));

    assertThatThrownBy(() -> deckService.getDeck(DECK_ID, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getCode()).isEqualTo("DECK_NOT_FOUND");
        });
    verifyNoInteractions(cardRepository, progressRepository);
  }

  @Test
  @DisplayName("Bộ không tồn tại: 404")
  void getDeck_missing_isNotFound() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> deckService.getDeck(DECK_ID, USER_ID))
        .isInstanceOfSatisfying(BusinessException.class,
            ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  // ===================== createDeck =====================

  @Test
  @DisplayName("Tạo bộ: trả 0 / 0 / 0 mà không chạy câu đếm nào")
  void createDeck_returnsZeroCounts_withoutQueries() {
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
    // save(x) trả về chính x, kèm id như DB vừa sinh.
    when(deckRepository.save(any(Deck.class))).thenAnswer(inv -> withId(inv.getArgument(0), DECK_ID));

    DeckResponse response = deckService.createDeck(new DeckRequest("  IELTS 7.0  ", null), USER_ID);

    assertThat(response.id()).isEqualTo(DECK_ID);
    assertThat(response.deckName()).isEqualTo("IELTS 7.0");   // Deck.apply đã strip()
    assertThat(response.cardCount()).isZero();
    assertThat(response.dueCount()).isZero();
    assertThat(response.newCount()).isZero();
    // Bắt được bug cũ: toResponse() chạy 3 câu count cho bộ vừa tạo.
    verifyNoInteractions(cardRepository, progressRepository);
  }

  // ===================== updateDeck =====================

  @Test
  @DisplayName("Sửa bộ: response mang updatedAt MỚI, không phải giá trị cũ")
  void updateDeck_refreshesUpdatedAt() {
    Deck mine = withId(Deck.personal("Bộ cũ", null, me), DECK_ID);
    Instant longAgo = Instant.parse("2026-01-01T00:00:00Z");
    ReflectionTestUtils.setField(mine, "updatedAt", longAgo);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(mine));

    DeckResponse response = deckService.updateDeck(DECK_ID, new DeckRequest("Bộ mới", "mô tả"), USER_ID);

    assertThat(response.deckName()).isEqualTo("Bộ mới");
    // Bắt được bug cũ: updatedAt chỉ đổi ở @PreUpdate (lúc flush), response vẫn mang longAgo.
    assertThat(response.updatedAt()).isAfter(longAgo);
    // Không gọi save(): dirty checking tự UPDATE cuối transaction.
    verify(deckRepository, never()).save(any());
  }

  @Test
  @DisplayName("Sửa bộ hệ thống: 403 DECK_READ_ONLY, tên bộ giữ nguyên")
  void updateDeck_systemDeck_isForbidden() {
    Deck system = withId(Deck.system("1000 từ", null), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(system));

    assertThatThrownBy(() -> deckService.updateDeck(DECK_ID, new DeckRequest("Đổi tên", null), USER_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(ex.getCode()).isEqualTo("DECK_READ_ONLY");
        });
    assertThat(system.getDeckName()).isEqualTo("1000 từ");
  }

  // ===================== helper =====================

  // Entity không có setId (id do DB sinh) → dùng ReflectionTestUtils gán thẳng vào field.
  private static Deck withId(Deck deck, Long id) {
    ReflectionTestUtils.setField(deck, "id", id);
    return deck;
  }

  private static User userWithId(Long id, String email) {
    User user = new User(email, "hash", "Tên");
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
