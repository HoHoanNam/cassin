package com.cassin.vocab;

import static com.cassin.vocab.VocabFixtures.sense;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.cassin.common.BusinessException;
import com.cassin.user.User;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;
import com.cassin.vocab.dto.SenseRequest;
import com.cassin.vocab.dto.WordRequest;
import com.cassin.vocab.dto.WordResponse;

/**
 * [V10] Admin quản lý bộ hệ thống + từ hệ thống. Bộ / từ của học viên → luôn 404.
 */
@ExtendWith(MockitoExtension.class)
class AdminDeckServiceTest {
  private static final Long DECK_ID = 10L;

  @Mock DeckRepository deckRepository;
  @Mock WordRepository wordRepository;
  @Mock CardProgressRepository progressRepository;
  @Mock DeckService deckService;

  @InjectMocks AdminDeckService adminDeckService;

  private final User student = VocabFixtures.user(1L, "an@gmail.com");
  private final Deck systemDeck = VocabFixtures.withId(Deck.system("1000 từ", null), DECK_ID);

  private static WordRequest improveRequest(String term) {
    return new WordRequest(term, "/ɪmˈpruːv/",
        List.of(new SenseRequest(null, PartOfSpeech.VERB, "cải thiện", List.of("Her English has [improved] a lot."))),
        null);
  }

  // ===================== Bộ =====================

  @Test
  @DisplayName("Tạo bộ hệ thống: SYSTEM_DECK, không ai sửa được qua API học viên")
  void createDeck_isSystem() {
    when(deckRepository.save(any(Deck.class))).thenAnswer(inv -> VocabFixtures.withId(inv.getArgument(0), DECK_ID));

    DeckResponse response = adminDeckService.createDeck(new DeckRequest("1000 từ", null));

    assertThat(response.deckType()).isEqualTo(DeckType.SYSTEM_DECK);
    assertThat(response.editable()).isFalse();
    assertThat(response.wordCount()).isZero();
  }

  @Test
  @DisplayName("Sửa bộ CÁ NHÂN qua admin: 404 (admin không đụng dữ liệu học viên)")
  void updateDeck_personalDeck_isNotFound() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(VocabFixtures.withId(Deck.personal("IELTS", null, student), DECK_ID)));

    assertStatus(() -> adminDeckService.updateDeck(DECK_ID, new DeckRequest("x", null)), HttpStatus.NOT_FOUND, "DECK_NOT_FOUND");
  }

  @Test
  @DisplayName("Xóa bộ đã có người học: 409 DECK_IN_USE, không xóa")
  void deleteDeck_inUse_isConflict() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(systemDeck));
    when(progressRepository.existsInDeck(DECK_ID)).thenReturn(true);

    assertStatus(() -> adminDeckService.deleteDeck(DECK_ID), HttpStatus.CONFLICT, "DECK_IN_USE");
    verify(deckRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Xóa bộ chưa ai học: xóa bộ, không xóa từ")
  void deleteDeck_unused_deletes() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(systemDeck));
    when(progressRepository.existsInDeck(DECK_ID)).thenReturn(false);

    adminDeckService.deleteDeck(DECK_ID);

    verify(deckRepository).delete(systemDeck);
    verifyNoInteractions(wordRepository);
  }

  // ===================== Từ =====================

  @Test
  @DisplayName("Tạo từ hệ thống: owner null, lưu rồi đặt vào bộ")
  void createWord_savesAndLinks() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(systemDeck));
    when(wordRepository.existsByOwnerIsNullAndTermIgnoreCase("improve")).thenReturn(false);
    when(deckRepository.findSystemContaining(any())).thenReturn(List.of(systemDeck));

    WordResponse response = adminDeckService.createWord(DECK_ID, improveRequest("  improve "));

    assertThat(response.term()).isEqualTo("improve");
    assertThat(response.system()).isTrue();
    assertThat(response.senses()).hasSize(1);
    verify(wordRepository).save(any(Word.class));
    verify(deckService).linkWord(any(Deck.class), any(Word.class));
  }

  @Test
  @DisplayName("Tạo từ trùng (không phân biệt hoa thường): 409 WORD_EXISTS, không lưu")
  void createWord_duplicate_isConflict() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(systemDeck));
    when(wordRepository.existsByOwnerIsNullAndTermIgnoreCase("Improve")).thenReturn(true);

    assertStatus(() -> adminDeckService.createWord(DECK_ID, improveRequest("Improve")), HttpStatus.CONFLICT, "WORD_EXISTS");
    verify(wordRepository, never()).save(any());
  }

  @Test
  @DisplayName("Đặt từ CỦA HỌC VIÊN vào bộ hệ thống: 404 WORD_NOT_FOUND")
  void addWord_personalWord_isNotFound() {
    Word studentWord = VocabFixtures.word(4L, "tackle", student, sense(PartOfSpeech.VERB, "giải quyết"));
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(systemDeck));
    when(wordRepository.findById(4L)).thenReturn(Optional.of(studentWord));

    assertStatus(() -> adminDeckService.addWord(DECK_ID, 4L), HttpStatus.NOT_FOUND, "WORD_NOT_FOUND");
    verifyNoInteractions(deckService);
  }

  @Test
  @DisplayName("Đổi tên trùng từ hệ thống khác: 409; đổi hoa thường của chính nó: không kiểm tra trùng")
  void updateWord_renameRules() {
    Word improve = VocabFixtures.word(3L, "improve", null, sense(PartOfSpeech.VERB, "cải thiện"));
    when(wordRepository.findById(3L)).thenReturn(Optional.of(improve));

    when(wordRepository.existsByOwnerIsNullAndTermIgnoreCase("enhance")).thenReturn(true);
    assertStatus(() -> adminDeckService.updateWord(3L, improveRequest("enhance")), HttpStatus.CONFLICT, "WORD_EXISTS");

    when(deckRepository.findSystemContaining(3L)).thenReturn(List.of());
    WordResponse response = adminDeckService.updateWord(3L, improveRequest("Improve"));
    assertThat(response.term()).isEqualTo("Improve");
    verify(wordRepository, never()).existsByOwnerIsNullAndTermIgnoreCase("Improve");
  }

  @Test
  @DisplayName("Nghĩa không hợp lệ (định nghĩa trống): 400 INVALID_WORD, từ giữ nguyên")
  void updateWord_invalidSense_isBadRequest() {
    Word improve = VocabFixtures.word(3L, "improve", null, sense(PartOfSpeech.VERB, "cải thiện"));
    when(wordRepository.findById(3L)).thenReturn(Optional.of(improve));
    WordRequest bad = new WordRequest("improve", null,
        List.of(new SenseRequest(null, PartOfSpeech.VERB, "   ", List.of())), null);

    assertStatus(() -> adminDeckService.updateWord(3L, bad), HttpStatus.BAD_REQUEST, "INVALID_WORD");
    assertThat(improve.getSenses()).extracting(s -> s.getDefinition()).containsExactly("cải thiện");
  }

  @Test
  @DisplayName("Bỏ từ khỏi bộ: chỉ xóa liên kết; không có dòng nào thì không đổi updatedAt")
  void removeWord_onlyLink() {
    when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(systemDeck));
    when(deckRepository.removeWord(DECK_ID, 3L)).thenReturn(1);

    adminDeckService.removeWord(DECK_ID, 3L);

    verify(deckRepository).removeWord(DECK_ID, 3L);
    verify(wordRepository, never()).delete(any());
  }

  @Test
  @DisplayName("Xóa từ cá nhân qua admin: 404, không xóa")
  void deleteWord_personalWord_isNotFound() {
    Word studentWord = VocabFixtures.word(4L, "tackle", student, sense(PartOfSpeech.VERB, "giải quyết"));
    when(wordRepository.findById(4L)).thenReturn(Optional.of(studentWord));

    assertStatus(() -> adminDeckService.deleteWord(4L), HttpStatus.NOT_FOUND, "WORD_NOT_FOUND");
    verify(wordRepository, never()).delete(any());
    verify(deckRepository, never()).removeWord(anyLong(), anyLong());
    verify(wordRepository, never()).existsByOwnerIsNullAndTermIgnoreCase(anyString());
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
