package com.cassin.vocab;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;
import com.cassin.vocab.dto.WordRequest;
import com.cassin.vocab.dto.WordResponse;

import lombok.RequiredArgsConstructor;

/**
 * Admin quản lý BỘ HỆ THỐNG và TỪ HỆ THỐNG (owner = null).
 * Bộ / từ của học viên → luôn 404 ở đây (admin không đụng vào dữ liệu cá nhân).
 *
 * [V10] Thẻ → Từ nhiều nghĩa. Một từ hệ thống nằm được trong nhiều bộ hệ thống:
 *   POST /admin/decks/{id}/words          tạo từ hệ thống MỚI và đặt vào bộ
 *   POST /admin/decks/{id}/words/{wordId} đặt một từ hệ thống CÓ SẴN vào bộ
 *   DELETE /admin/decks/{id}/words/{wordId} bỏ từ khỏi bộ (từ vẫn còn)
 *   PUT / DELETE /admin/words/{wordId}    sửa / xóa hẳn từ hệ thống
 */
@Service
@RequiredArgsConstructor
public class AdminDeckService {
  private final DeckRepository deckRepository;
  private final WordRepository wordRepository;
  private final CardProgressRepository progressRepository;
  private final DeckService deckService; // dùng lại linkWord (kiểm tra Deck.canContain)

  // ===================== Bộ hệ thống =====================

  @Transactional
  public DeckResponse createDeck(DeckRequest r) {
    Deck deck = deckRepository.save(Deck.system(r.deckName(), r.description()));
    // Admin không "học" bộ → due / new = 0. userId null → editable = false.
    return DeckResponse.from(deck, 0L, 0L, 0L, null);
  }

  @Transactional
  public DeckResponse updateDeck(Long deckId, DeckRequest r) {
    Deck deck = findSystemDeck(deckId);
    deck.update(r.deckName(), r.description());
    return DeckResponse.from(deck, deckRepository.countWords(deckId), 0L, 0L, null);
  }

  /**
   * Chỉ xóa bộ (và dòng deck_words). Từ hệ thống trong bộ vẫn còn, có thể nằm trong bộ khác.
   * Tìm bộ TRƯỚC (404 cho bộ cá nhân), kiểm tra người học SAU (409).
   */
  @Transactional
  public void deleteDeck(Long deckId) {
    Deck deck = findSystemDeck(deckId);
    if (progressRepository.existsInDeck(deckId)) {
      throw new BusinessException(HttpStatus.CONFLICT, "DECK_IN_USE", "Bộ từ đã có người học, không thể xóa");
    }
    deckRepository.delete(deck);
  }

  // ===================== Từ hệ thống =====================

  /** Tạo từ hệ thống mới (kèm nhiều nghĩa) và đặt vào bộ. deckIds trong request bị bỏ qua. */
  @Transactional
  public WordResponse createWord(Long deckId, WordRequest r) {
    Deck deck = findSystemDeck(deckId);
    if (wordRepository.existsByOwnerIsNullAndTermIgnoreCase(r.term().strip())) {
      throw new BusinessException(HttpStatus.CONFLICT, "WORD_EXISTS",
          "Từ \"" + r.term().strip() + "\" đã có trong hệ thống. Hãy đặt từ có sẵn vào bộ hoặc thêm nghĩa cho từ đó.");
    }
    Word word = WordService.guard(() -> {
      Word w = Word.system(r.term(), r.phonetic());
      w.replaceSenses(r.senseInputs());
      return w;
    });
    wordRepository.save(word);
    deckService.linkWord(deck, word);
    return toResponse(word);
  }

  /** Đặt một từ hệ thống CÓ SẴN vào bộ hệ thống (từ dùng chung cho nhiều bộ). */
  @Transactional
  public void addWord(Long deckId, Long wordId) {
    deckService.linkWord(findSystemDeck(deckId), findSystemWord(wordId));
  }

  @Transactional
  public void removeWord(Long deckId, Long wordId) {
    Deck deck = findSystemDeck(deckId);
    if (deckRepository.removeWord(deck.getId(), wordId) > 0) {
      deck.touch();
    }
  }

  /** Sửa từ hệ thống + toàn bộ nghĩa (giữ id nghĩa → giữ tiến độ của học viên). */
  @Transactional
  public WordResponse updateWord(Long wordId, WordRequest r) {
    Word word = findSystemWord(wordId);
    boolean renamed = !word.getTerm().equalsIgnoreCase(r.term().strip());
    if (renamed && wordRepository.existsByOwnerIsNullAndTermIgnoreCase(r.term().strip())) {
      throw new BusinessException(HttpStatus.CONFLICT, "WORD_EXISTS",
          "Từ \"" + r.term().strip() + "\" đã có trong hệ thống");
    }
    WordService.guard(() -> {
      word.update(r.term(), r.phonetic());
      word.replaceSenses(r.senseInputs());
      return word;
    });
    return toResponse(word);
  }

  /**
   * Xóa hẳn một từ hệ thống. Tiến độ của học viên với từ này bị xóa theo (giống xóa thẻ trước V10):
   * xóa một từ sai / trùng là sửa nội dung bình thường, khác với xóa cả bộ.
   */
  @Transactional
  public void deleteWord(Long wordId) {
    wordRepository.delete(findSystemWord(wordId));
  }

  // ===================== helpers =====================

  private Deck findSystemDeck(Long deckId) {
    return deckRepository.findById(deckId)
        .filter(d -> d.getDeckType() == DeckType.SYSTEM_DECK)
        .orElseThrow(DeckService::deckNotFound);
  }

  private Word findSystemWord(Long wordId) {
    return wordRepository.findById(wordId)
        .filter(w -> w.isSystem())
        .orElseThrow(DeckService::wordNotFound);
  }

  private WordResponse toResponse(Word word) {
    // Chỉ liệt kê bộ hệ thống. userId null → editable = false (admin sửa qua /api/admin, không qua /api/words).
    return WordResponse.from(word, deckRepository.findSystemContaining(word.getId()), null);
  }
}
