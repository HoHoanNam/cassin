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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.cassin.common.BusinessException;
import com.cassin.common.TimeConfig;
import com.cassin.common.dto.PageResponse;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;
import com.cassin.vocab.dto.DeckSummary;
import com.cassin.vocab.dto.WordSummary;

/**
 * [V10] DeckService: bộ là nhãn chủ đề chứa TỪ; due / new đếm theo NGHĨA.
 */
@ExtendWith(MockitoExtension.class)
class DeckServiceTest {
  private static final Long USER_ID = 1L;
  private static final Long DECK_ID = 10L;
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

  @Mock DeckRepository deckRepository;
  @Mock WordRepository wordRepository;
  @Mock SenseRepository senseRepository;
  @Mock UserRepository userRepository;
  @Mock CardProgressRepository progressRepository;

  private DeckService deckService;

  private final User me = VocabFixtures.user(USER_ID, "an@gmail.com");
  private final User other = VocabFixtures.user(2L, "binh@gmail.com");

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(TimeConfig.APP_ZONE).toInstant(), TimeConfig.APP_ZONE);
    // Thứ tự = thứ tự field final trong DeckService.
    deckService = new DeckService(deckRepository, wordRepository, senseRepository, userRepository, progressRepository, fixedClock);
  }

  // ===================== listDecks / getDeck =====================

  @Test
  @DisplayName("Danh sách bộ: đúng 'hôm nay', chép đủ wordCount / dueCount / newCount, không đếm lẻ từng bộ")
  void listDecks_mapsSummaries() {
    Instant updated = Instant.parse("2026-11-01T03:00:00Z");
    DeckSummary system = new DeckSummary(5L, "1000 từ", null, DeckType.SYSTEM_DECK, null, 22L, 4L, 20L, updated);
    DeckSummary mine = new DeckSummary(7L, "IELTS", null, DeckType.USER_DECK, USER_ID, 0L, 0L, 0L, updated);
    when(deckRepository.findVisibleSummaries(USER_ID, TODAY)).thenReturn(List.of(system, mine));

    List<DeckResponse> decks = deckService.listDecks(USER_ID);

    assertThat(decks)
        .extracting(d -> d.id(), d -> d.wordCount(), d -> d.dueCount(), d -> d.newCount(), d -> d.editable())
        .containsExactly(
            tuple(5L, 22L, 4L, 20L, false),
            tuple(7L, 0L, 0L, 0L, true));
    verifyNoInteractions(wordRepository, senseRepository, progressRepository);
  }

  @Test
  @DisplayName("Chi tiết bộ: số từ + số nghĩa đến hạn + số nghĩa mới, đúng tham số")
  void getDeck_counts() {
    Deck deck = VocabFixtures.withId(Deck.system("1000 từ", null), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));
    when(deckRepository.countWords(DECK_ID)).thenReturn(22L);
    when(progressRepository.countDueInDeck(DECK_ID, USER_ID, TODAY)).thenReturn(4L);
    when(senseRepository.countNewInDeck(DECK_ID, USER_ID)).thenReturn(20L);

    DeckResponse response = deckService.getDeck(DECK_ID, USER_ID);

    assertThat(response.wordCount()).isEqualTo(22L);
    assertThat(response.dueCount()).isEqualTo(4L);
    assertThat(response.newCount()).isEqualTo(20L);
  }

  @Test
  @DisplayName("Bộ của người khác: 404, không đếm gì")
  void getDeck_othersDeck_isNotFound() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(VocabFixtures.withId(Deck.personal("Bộ của Bình", null, other), DECK_ID)));

    assertStatus(() -> deckService.getDeck(DECK_ID, USER_ID), HttpStatus.NOT_FOUND, "DECK_NOT_FOUND");
    verifyNoInteractions(progressRepository, senseRepository);
  }

  // ===================== create / update / delete =====================

  @Test
  @DisplayName("Tạo bộ: 0 / 0 / 0, không chạy câu đếm nào")
  void createDeck_zeroCounts() {
    when(userRepository.getReferenceById(USER_ID)).thenReturn(me);
    when(deckRepository.save(any(Deck.class))).thenAnswer(inv -> VocabFixtures.withId(inv.getArgument(0), DECK_ID));

    DeckResponse response = deckService.createDeck(new DeckRequest("IELTS", null), USER_ID);

    assertThat(response.wordCount()).isZero();
    assertThat(response.editable()).isTrue();
    verify(deckRepository, never()).countWords(anyLong());
  }

  @Test
  @DisplayName("Sửa bộ: updatedAt mới ngay trong response")
  void updateDeck_refreshesUpdatedAt() {
    Deck mine = VocabFixtures.withId(Deck.personal("Cũ", null, me), DECK_ID);
    Instant longAgo = Instant.parse("2026-01-01T00:00:00Z");
    ReflectionTestUtils.setField(mine, "updatedAt", longAgo);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(mine));

    DeckResponse response = deckService.updateDeck(DECK_ID, new DeckRequest("Mới", null), USER_ID);

    assertThat(response.deckName()).isEqualTo("Mới");
    assertThat(response.updatedAt()).isAfter(longAgo);
  }

  @Test
  @DisplayName("Sửa bộ hệ thống: 403 DECK_READ_ONLY")
  void updateDeck_systemDeck_isForbidden() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(VocabFixtures.withId(Deck.system("1000 từ", null), DECK_ID)));

    assertStatus(() -> deckService.updateDeck(DECK_ID, new DeckRequest("x", null), USER_ID), HttpStatus.FORBIDDEN, "DECK_READ_ONLY");
  }

  @Test
  @DisplayName("Xóa bộ cá nhân: chỉ xóa bộ, KHÔNG xóa từ (từ vẫn trong sổ từ)")
  void deleteDeck_keepsWords() {
    Deck mine = VocabFixtures.withId(Deck.personal("IELTS", null, me), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(mine));

    deckService.deleteDeck(DECK_ID, USER_ID);

    verify(deckRepository).delete(mine);
    verifyNoInteractions(wordRepository);
  }

  // ===================== [V10] Từ trong bộ =====================

  @Test
  @DisplayName("Danh sách từ trong bộ: q null đổi thành chuỗi rỗng, map sang WordSummary")
  void listWords_mapsPage() {
    Deck deck = VocabFixtures.withId(Deck.system("1000 từ", null), DECK_ID);
    Word light = VocabFixtures.word(3L, "light", null, sense(PartOfSpeech.NOUN, "ánh sáng"), sense(PartOfSpeech.ADJECTIVE, "nhẹ"));
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));
    when(wordRepository.findInDeck(eq(DECK_ID), eq(""), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(light)));

    PageResponse<WordSummary> page = deckService.listWords(DECK_ID, null, 0, 20, USER_ID);

    assertThat(page.content()).singleElement().satisfies(w -> {
      assertThat(w.term()).isEqualTo("light");
      assertThat(w.senses()).hasSize(2);
      assertThat(w.system()).isTrue();
      assertThat(w.editable()).isFalse();
    });
  }

  @Test
  @DisplayName("Đặt từ hệ thống vào bộ CỦA MÌNH: chèn deck_words, cập nhật thời gian bộ")
  void addWord_systemWordToOwnDeck() {
    Deck mine = VocabFixtures.withId(Deck.personal("IELTS", null, me), DECK_ID);
    Instant longAgo = Instant.parse("2026-01-01T00:00:00Z");
    ReflectionTestUtils.setField(mine, "updatedAt", longAgo);
    Word improve = VocabFixtures.word(3L, "improve", null, sense(PartOfSpeech.VERB, "cải thiện"));
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(mine));
    when(wordRepository.findById(3L)).thenReturn(Optional.of(improve));

    deckService.addWord(DECK_ID, 3L, USER_ID);

    verify(deckRepository).addWord(DECK_ID, 3L);
    assertThat(mine.getUpdatedAt()).isAfter(longAgo);
  }

  @Test
  @DisplayName("Đặt từ của NGƯỜI KHÁC vào bộ: 404 WORD_NOT_FOUND, không chèn gì")
  void addWord_othersWord_isNotFound() {
    Deck mine = VocabFixtures.withId(Deck.personal("IELTS", null, me), DECK_ID);
    Word binhWord = VocabFixtures.word(4L, "tackle", other, sense(PartOfSpeech.VERB, "giải quyết"));
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(mine));
    when(wordRepository.findById(4L)).thenReturn(Optional.of(binhWord));

    assertStatus(() -> deckService.addWord(DECK_ID, 4L, USER_ID), HttpStatus.NOT_FOUND, "WORD_NOT_FOUND");
    verify(deckRepository, never()).addWord(anyLong(), anyLong());
  }

  @Test
  @DisplayName("Đặt từ vào bộ HỆ THỐNG (học viên): 403 DECK_READ_ONLY")
  void addWord_toSystemDeck_isForbidden() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(VocabFixtures.withId(Deck.system("1000 từ", null), DECK_ID)));

    assertStatus(() -> deckService.addWord(DECK_ID, 3L, USER_ID), HttpStatus.FORBIDDEN, "DECK_READ_ONLY");
    verifyNoInteractions(wordRepository);
  }

  @Test
  @DisplayName("Bỏ từ khỏi bộ: chỉ xóa dòng deck_words")
  void removeWord_deletesLinkOnly() {
    Deck mine = VocabFixtures.withId(Deck.personal("IELTS", null, me), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(mine));
    when(deckRepository.removeWord(DECK_ID, 3L)).thenReturn(1);

    deckService.removeWord(DECK_ID, 3L, USER_ID);

    verify(deckRepository).removeWord(DECK_ID, 3L);
    verifyNoInteractions(wordRepository);
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
