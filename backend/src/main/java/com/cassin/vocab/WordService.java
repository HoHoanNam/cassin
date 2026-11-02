package com.cassin.vocab;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.BusinessException;
import com.cassin.common.dto.PageResponse;
import com.cassin.user.UserRepository;
import com.cassin.vocab.dto.WordRequest;
import com.cassin.vocab.dto.WordResponse;
import com.cassin.vocab.dto.WordSummary;

import lombok.RequiredArgsConstructor;

/**
 * "Sổ từ của tôi": tạo / sửa / xóa từ (kèm nhiều nghĩa), xem chi tiết, tìm kiếm.
 * Thay cho CardService cũ.
 *
 * Quyền:
 *   - Xem : từ của mình + từ hệ thống
 *   - Sửa / xóa: CHỈ từ của mình (từ hệ thống do admin quản lý qua AdminDeckService)
 */
@Service
@RequiredArgsConstructor
public class WordService {
  private final WordRepository wordRepository;
  private final SenseRepository senseRepository;
  private final DeckRepository deckRepository;
  private final UserRepository userRepository;
  private final DeckService deckService;   // dùng lại luật quyền của bộ và của từ

  /** GET /api/words?q=&deckId=: sổ từ (deckId null) hoặc từ trong một bộ. */
  @Transactional(readOnly = true)
  public PageResponse<WordSummary> listWords(Long deckId, String q, int page, int size, Long userId) {
    if (deckId != null) {
      return deckService.listWords(deckId, q, page, size, userId);
    }
    PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, DeckService.MAX_PAGE_SIZE));
    return PageResponse.from(
        wordRepository.findNotebook(userId, q == null ? "" : q.strip(), pageable)
            .map(w -> WordSummary.from(w, userId)));
  }

  @Transactional(readOnly = true)
  public WordResponse getWord(Long wordId, Long userId) {
    Word word = deckService.findReadableWord(wordId, userId);
    return toResponse(word, userId);
  }

  @Transactional
  public WordResponse createWord(WordRequest r, Long userId) {
    if (wordRepository.existsByOwnerIdAndTermIgnoreCase(userId, r.term().strip())) {
      throw wordExists(r.term());
    }
    Word word = guard(() -> {
      Word w = Word.personal(r.term(), r.phonetic(), userRepository.getReferenceById(userId));
      w.replaceSenses(r.senseInputs());
      return w;
    });
    // save TRƯỚC khi gắn vào bộ: cần id của từ để chèn dòng deck_words.
    wordRepository.save(word);
    if (r.deckIds() != null) {
      for (Long deckId : new HashSet<>(r.deckIds())) {
        deckService.linkWord(deckService.findEditable(deckId, userId), word);
      }
    }
    return toResponse(word, userId);
  }

  /**
   * Sửa từ + toàn bộ nghĩa. Nghĩa giữ id thì giữ tiến độ ôn; nghĩa bị bỏ khỏi danh sách thì bị xóa.
   * deckIds != null: đồng bộ các bộ CÁ NHÂN chứa từ này cho khớp đúng danh sách gửi lên.
   */
  @Transactional
  public WordResponse updateWord(Long wordId, WordRequest r, Long userId) {
    Word word = findEditableWord(wordId, userId);
    boolean renamed = !word.getTerm().equalsIgnoreCase(r.term().strip());
    if (renamed && wordRepository.existsByOwnerIdAndTermIgnoreCase(userId, r.term().strip())) {
      throw wordExists(r.term());
    }
    guard(() -> {
      word.update(r.term(), r.phonetic());
      word.replaceSenses(r.senseInputs());
      return word;
    });
    if (r.deckIds() != null) {
      syncOwnDecks(word, Set.copyOf(r.deckIds()), userId);
    }
    return toResponse(word, userId);
  }

  /**
   * Xóa từ: kéo theo các nghĩa (cascade ALL ở Word.senses), câu ví dụ, tiến độ ôn, nhật ký luyện
   * và dòng deck_words (ON DELETE CASCADE trong DB).
   */
  @Transactional
  public void deleteWord(Long wordId, Long userId) {
    wordRepository.delete(findEditableWord(wordId, userId));
  }

  /**
   * Dùng cho package practice / review: tìm một nghĩa user XEM được (của từ mình hoặc từ hệ thống).
   * public vì PracticeService nằm ở package khác.
   */
  @Transactional(readOnly = true)
  public Sense findReadableSense(Long senseId, Long userId) {
    Sense sense = senseRepository.findById(senseId).orElseThrow(
        () -> new BusinessException(HttpStatus.NOT_FOUND, "SENSE_NOT_FOUND", "Không tìm thấy nghĩa"));
    if (!sense.getWord().isReadableBy(userId)) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "SENSE_NOT_FOUND", "Không tìm thấy nghĩa");
    }
    return sense;
  }

  /**
   * Nguồn câu hỏi luyện tập: các nghĩa CÓ câu ví dụ, trong một bộ (deckId) hoặc trong sổ từ (null).
   * public vì PracticeService nằm ở package khác; luật quyền của bộ vẫn nằm trong vocab.
   */
  @Transactional(readOnly = true)
  public List<Sense> practiceSenses(Long deckId, Long userId) {
    if (deckId != null) {
      deckService.findReadable(deckId, userId);
      return senseRepository.findPracticeInDeck(deckId);
    }
    return senseRepository.findPracticeInNotebook(userId);
  }

  // ===================== helpers =====================

  private Word findEditableWord(Long wordId, Long userId) {
    Word word = deckService.findReadableWord(wordId, userId);
    if (!word.isOwnedBy(userId)) {   // đọc được mà không phải của mình → là từ hệ thống
      throw new BusinessException(HttpStatus.FORBIDDEN, "WORD_READ_ONLY", "Từ hệ thống chỉ để học, không sửa được");
    }
    return word;
  }

  /** Bỏ từ khỏi các bộ cá nhân không còn trong danh sách, thêm vào các bộ mới. Bộ hệ thống không đụng tới. */
  private void syncOwnDecks(Word word, Set<Long> wanted, Long userId) {
    Set<Long> current = new HashSet<>(deckRepository.findOwnDeckIdsContaining(word.getId(), userId));
    for (Long deckId : current) {
      if (!wanted.contains(deckId)) {
        deckRepository.removeWord(deckId, word.getId());
      }
    }
    for (Long deckId : wanted) {
      if (!current.contains(deckId)) {
        deckService.linkWord(deckService.findEditable(deckId, userId), word);
      }
    }
  }

  private WordResponse toResponse(Word word, Long userId) {
    return WordResponse.from(word, deckRepository.findVisibleContaining(word.getId(), userId), userId);
  }

  private static BusinessException wordExists(String term) {
    return new BusinessException(HttpStatus.CONFLICT, "WORD_EXISTS",
        "Từ \"" + term.strip() + "\" đã có trong sổ từ. Hãy thêm nghĩa mới vào từ đó.");
  }

  /** Luật của entity (IllegalArgumentException) → 400 INVALID_WORD, cùng cách AdminGrammarService.guard. */
  static <T> T guard(Supplier<T> action) {
    try {
      return action.get();
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_WORD", ex.getMessage());
    }
  }
}
