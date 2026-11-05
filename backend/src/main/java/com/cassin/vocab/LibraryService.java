package com.cassin.vocab;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cassin.common.dto.PageResponse;
import com.cassin.vocab.dto.LibraryWord;

import lombok.RequiredArgsConstructor;

/**
 * Thư viện từ: duyệt TỪ HỆ THỐNG để đặt vào bộ của mình.
 * Đặt vào bộ dùng API có sẵn: POST /api/decks/{deckId}/words/{wordId} (DeckService.addWord).
 */
@Service
@RequiredArgsConstructor
public class LibraryService {
  static final int MAX_PAGE_SIZE = 100;

  private final WordRepository wordRepository;

  /** GET /api/library?q=&onlyNew=&page=&size= */
  @Transactional(readOnly = true)
  public PageResponse<LibraryWord> search(Long userId, String q, boolean onlyNew, int page, int size) {
    PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    // q = "" thay cho null: like '%%' khớp mọi từ (cùng quy ước với findNotebook)
    Page<Word> words = wordRepository.findLibrary(userId, q == null ? "" : q.strip(), onlyNew, pageable);

    // Trang rỗng → không cần hỏi thêm. (Một số CSDL báo lỗi cú pháp với "in ()" rỗng.)
    List<Long> ids = words.map(w -> w.getId()).getContent();
    Set<Long> inMyDecks = ids.isEmpty() ? Set.of() : Set.copyOf(wordRepository.findInOwnDecks(userId, ids));
    Set<Long> studying = ids.isEmpty() ? Set.of() : Set.copyOf(wordRepository.findStudiedWords(userId, ids));

    return PageResponse
        .from(words.map(w -> LibraryWord.from(w, inMyDecks.contains(w.getId()), studying.contains(w.getId()))));
  }
}