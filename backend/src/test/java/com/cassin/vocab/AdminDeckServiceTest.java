package com.cassin.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.cassin.user.User;
import com.cassin.vocab.dto.CardRequest;
import com.cassin.vocab.dto.CardResponse;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;

/**
 * [THÊM] Unit test cho AdminDeckService.
 *
 * Hai điều quan trọng nhất cần giữ:
 *   1. Admin CHỈ đụng được bộ HỆ THỐNG. Bộ cá nhân của user → luôn 404, kể cả khi bộ đó có người học.
 *   2. Bộ đã có người học thì không xóa được (409 DECK_IN_USE).
 * Việc "chỉ ADMIN mới gọi được" do SecurityConfig lo, không test ở đây.
 */
@ExtendWith(MockitoExtension.class)
class AdminDeckServiceTest {
  private static final Long DECK_ID = 10L;
  private static final Long CARD_ID = 100L;

  @Mock DeckRepository deckRepository;
  @Mock CardRepository cardRepository;
  @Mock CardProgressRepository progressRepository;

  private AdminDeckService service;

  @BeforeEach
  void setUp() {
    service = new AdminDeckService(deckRepository, cardRepository, progressRepository);
  }

  // ===================== deck =====================

  @Test
  @DisplayName("Tạo bộ: luôn là bộ HỆ THỐNG, không chủ, số đếm = 0, không chạy câu đếm")
  void createDeck_createsSystemDeck() {
    when(deckRepository.save(any(Deck.class))).thenAnswer(inv -> withId(inv.getArgument(0), DECK_ID));

    DeckResponse response = service.createDeck(new DeckRequest("3000 từ Oxford", null));

    assertThat(response.id()).isEqualTo(DECK_ID);
    assertThat(response.deckType()).isEqualTo(DeckType.SYSTEM_DECK);
    assertThat(response.cardCount()).isZero();
    verifyNoInteractions(cardRepository, progressRepository);
  }

  @Test
  @DisplayName("Sửa bộ: số thẻ đếm THẬT, không trả 0 (bug cũ)")
  void updateDeck_keepsRealCardCount() {
    Deck deck = withId(Deck.system("Tên cũ", null), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));
    when(cardRepository.countByDeckId(DECK_ID)).thenReturn(50L);

    DeckResponse response = service.updateDeck(DECK_ID, new DeckRequest("Tên mới", "Mô tả"));

    assertThat(response.deckName()).isEqualTo("Tên mới");
    assertThat(response.cardCount()).isEqualTo(50L);
    // Không gọi save(): dirty checking tự UPDATE.
    verify(deckRepository, never()).save(any());
  }

  @Test
  @DisplayName("Sửa bộ CÁ NHÂN của user: 404, tên bộ giữ nguyên")
  void updateDeck_personalDeck_isNotFound() {
    Deck personal = personalDeck();
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(personal));

    assertNotFound(() -> service.updateDeck(DECK_ID, new DeckRequest("Hack", null)), "DECK_NOT_FOUND");
    assertThat(personal.getDeckName()).isEqualTo("Bộ của An");
  }

  @Test
  @DisplayName("Xóa bộ hệ thống chưa ai học: xóa được")
  void deleteDeck_unused_isDeleted() {
    Deck deck = withId(Deck.system("Bộ thử", null), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));
    when(progressRepository.existsByCardDeckId(DECK_ID)).thenReturn(false);

    service.deleteDeck(DECK_ID);

    verify(deckRepository).delete(deck);
  }

  @Test
  @DisplayName("Xóa bộ đã có người học: 409 DECK_IN_USE, không xóa")
  void deleteDeck_inUse_isConflict() {
    Deck deck = withId(Deck.system("1000 từ", null), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));
    when(progressRepository.existsByCardDeckId(DECK_ID)).thenReturn(true);

    assertThatThrownBy(() -> service.deleteDeck(DECK_ID))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getCode()).isEqualTo("DECK_IN_USE");
        });
    verify(deckRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Xóa bộ CÁ NHÂN đã có người học: vẫn 404, KHÔNG để lộ 409 (bug thứ tự kiểm tra)")
  void deleteDeck_personalDeck_isNotFound_notConflict() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(personalDeck()));

    assertNotFound(() -> service.deleteDeck(DECK_ID), "DECK_NOT_FOUND");
    // Tìm bộ thất bại thì không được hỏi tới tiến độ học (trước đây hỏi TRƯỚC → trả 409).
    verifyNoInteractions(progressRepository);
    verify(deckRepository, never()).delete(any());
  }

  // ===================== card =====================

  @Test
  @DisplayName("Thêm thẻ vào bộ hệ thống: lưu và trả thẻ")
  void addCard_toSystemDeck() {
    Deck deck = withId(Deck.system("1000 từ", null), DECK_ID);
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));
    when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

    CardResponse response = service.addCard(DECK_ID, cardRequest("improve"));

    assertThat(response.word()).isEqualTo("improve");
    verify(cardRepository).save(any(Card.class));
  }

  @Test
  @DisplayName("Thêm thẻ vào bộ CÁ NHÂN: 404, không lưu")
  void addCard_toPersonalDeck_isNotFound() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(personalDeck()));

    assertNotFound(() -> service.addCard(DECK_ID, cardRequest("improve")), "DECK_NOT_FOUND");
    verify(cardRepository, never()).save(any());
  }

  @Test
  @DisplayName("Sửa thẻ thuộc bộ CÁ NHÂN: 404 CARD_NOT_FOUND, thẻ giữ nguyên")
  void updateCard_inPersonalDeck_isNotFound() {
    Card card = Card.create("achieve", PartOfSpeech.VERB, null, "đạt được", List.of(), personalDeck());
    when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));

    assertNotFound(() -> service.updateCard(CARD_ID, cardRequest("hacked")), "CARD_NOT_FOUND");
    assertThat(card.getWord()).isEqualTo("achieve");
  }

  @Test
  @DisplayName("Xóa thẻ không tồn tại: 404")
  void deleteCard_missing_isNotFound() {
    when(cardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

    assertNotFound(() -> service.deleteCard(CARD_ID), "CARD_NOT_FOUND");
    verify(cardRepository, never()).delete(any());
  }

  // ===================== helper =====================

  private static void assertNotFound(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getCode()).isEqualTo(code);
        });
  }

  private static Deck personalDeck() {
    User owner = new User("an@gmail.com", "hash", "An");
    ReflectionTestUtils.setField(owner, "id", 1L);
    return withId(Deck.personal("Bộ của An", null, owner), DECK_ID);
  }

  private static Deck withId(Deck deck, Long id) {
    ReflectionTestUtils.setField(deck, "id", id);
    return deck;
  }

  private static CardRequest cardRequest(String word) {
    return new CardRequest(word, PartOfSpeech.VERB, null, "nghĩa của " + word, List.of());
  }
}
