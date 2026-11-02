package com.cassin.vocab;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.user.User;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;
import com.cassin.vocab.dto.WordSummary;

import lombok.RequiredArgsConstructor;

/**
 * Bộ từ của học viên + luật quyền dùng chung trong package vocab.
 *
 * [V10] Bộ là NHÃN CHỦ ĐỀ: thêm / bỏ từ khỏi bộ không tạo / xóa từ.
 * Xóa bộ cá nhân thì từ vẫn còn trong "Sổ từ của tôi".
 */
@Service
@RequiredArgsConstructor
public class DeckService {
  static final int MAX_PAGE_SIZE = 100;

  private final DeckRepository deckRepository;
  private final WordRepository wordRepository;
  private final SenseRepository senseRepository;
  private final UserRepository userRepository;
  private final CardProgressRepository progressRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<DeckResponse> listDecks(Long userId) {
    return deckRepository.findVisibleSummaries(userId, LocalDate.now(clock))
        .stream()
        .map(s -> DeckResponse.from(s, userId))
        .toList();
  }

  @Transactional(readOnly = true)
  public DeckResponse getDeck(Long deckId, Long userId) {
    return toResponse(findReadable(deckId, userId), userId);
  }

  @Transactional
  public DeckResponse createDeck(DeckRequest request, Long userId) {
    // getReferenceById: không chạy SELECT, chỉ tạo object "đại diện" mang sẵn id để ghi owner_id.
    User owner = userRepository.getReferenceById(userId);
    Deck deck = deckRepository.save(Deck.personal(request.deckName(), request.description(), owner));
    return DeckResponse.from(deck, 0L, 0L, 0L, userId);   // bộ mới: chắc chắn 0 / 0 / 0
  }

  @Transactional
  public DeckResponse updateDeck(Long deckId, DeckRequest request, Long userId) {
    Deck deck = findEditable(deckId, userId);
    deck.update(request.deckName(), request.description());
    return toResponse(deck, userId);
  }

  /** [V10] Chỉ xóa bộ. Dòng deck_words do DB tự xóa (ON DELETE CASCADE), từ vẫn còn trong sổ từ. */
  @Transactional
  public void deleteDeck(Long deckId, Long userId) {
    deckRepository.delete(findEditable(deckId, userId));
  }

  // ===================== [V10] Từ trong bộ =====================

  /** GET /api/decks/{deckId}/words: các từ trong bộ, có tìm kiếm và phân trang. */
  @Transactional(readOnly = true)
  public PageResponse<WordSummary> listWords(Long deckId, String q, int page, int size, Long userId) {
    findReadable(deckId, userId);
    PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    return PageResponse.from(
        wordRepository.findInDeck(deckId, q == null ? "" : q.strip(), pageable)
            .map(w -> WordSummary.from(w, userId)));
  }

  /**
   * POST /api/decks/{deckId}/words/{wordId}: đặt một từ (của mình hoặc từ hệ thống) vào bộ của mình.
   * Từ đã có trong bộ → không lỗi, không làm gì (on conflict do nothing).
   */
  @Transactional
  public void addWord(Long deckId, Long wordId, Long userId) {
    Deck deck = findEditable(deckId, userId);
    Word word = findReadableWord(wordId, userId);
    linkWord(deck, word);
  }

  /** DELETE /api/decks/{deckId}/words/{wordId}: bỏ từ khỏi bộ (từ KHÔNG bị xóa). */
  @Transactional
  public void removeWord(Long deckId, Long wordId, Long userId) {
    Deck deck = findEditable(deckId, userId);
    if (deckRepository.removeWord(deck.getId(), wordId) > 0) {
      deck.touch();
    }
  }

  // ===== Luật quyền: dùng chung trong package vocab (không ghi "public") =====

  /** Bộ đọc được: bộ hệ thống, hoặc bộ của chính user. Bộ của người khác → 404 (không để lộ). */
  Deck findReadable(Long deckId, Long userId) {
    Deck deck = deckRepository.findById(deckId).orElseThrow(DeckService::deckNotFound);
    checkReadable(deck, userId);
    return deck;
  }

  /** Bộ sửa được: chỉ bộ của chính user. Bộ hệ thống → 403. */
  Deck findEditable(Long deckId, Long userId) {
    Deck deck = findReadable(deckId, userId);
    checkEditable(deck, userId);
    return deck;
  }

  void checkReadable(Deck deck, Long userId) {
    boolean readable = deck.getDeckType() == DeckType.SYSTEM_DECK || deck.isOwnedBy(userId);
    if (!readable) {
      throw deckNotFound();
    }
  }

  void checkEditable(Deck deck, Long userId) {
    checkReadable(deck, userId);
    if (!deck.isOwnedBy(userId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "DECK_READ_ONLY", "Bộ từ hệ thống chỉ để ôn, không sửa được");
    }
  }

  /** [V10] Từ đọc được: từ của mình hoặc từ hệ thống. Từ của người khác → 404. */
  Word findReadableWord(Long wordId, Long userId) {
    Word word = wordRepository.findById(wordId).orElseThrow(DeckService::wordNotFound);
    if (!word.isReadableBy(userId)) {
      throw wordNotFound();
    }
    return word;
  }

  /** [V10] Đặt từ vào bộ, kiểm tra luật Deck.canContain trước. Dùng chung với WordService / AdminDeckService. */
  void linkWord(Deck deck, Word word) {
    if (!deck.canContain(word)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "WORD_NOT_ALLOWED",
          "Bộ hệ thống chỉ chứa từ hệ thống");
    }
    deckRepository.addWord(deck.getId(), word.getId());
    deck.touch();
  }

  static BusinessException deckNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "DECK_NOT_FOUND", "Không tìm thấy bộ từ");
  }

  static BusinessException wordNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "WORD_NOT_FOUND", "Không tìm thấy từ");
  }

  private DeckResponse toResponse(Deck deck, Long userId) {
    LocalDate today = LocalDate.now(clock);
    return DeckResponse.from(
        deck,
        deckRepository.countWords(deck.getId()),
        progressRepository.countDueInDeck(deck.getId(), userId, today),
        senseRepository.countNewInDeck(deck.getId(), userId),
        userId);
  }
}
