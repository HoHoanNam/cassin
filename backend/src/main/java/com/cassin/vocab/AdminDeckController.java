package com.cassin.vocab;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;
import com.cassin.vocab.dto.WordRequest;
import com.cassin.vocab.dto.WordResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * /api/admin/**: SecurityConfig chỉ cho ROLE_ADMIN.
 * Phần ĐỌC dùng lại API của học viên: GET /api/decks, /api/decks/{id}/words, /api/words/{id}
 * (bộ và từ hệ thống ai cũng xem được).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDeckController {
  private final AdminDeckService adminDeckService;

  // ---------- Bộ ----------

  @PostMapping("/decks")
  @ResponseStatus(HttpStatus.CREATED)
  public DeckResponse createDeck(@Valid @RequestBody DeckRequest request) {
    return adminDeckService.createDeck(request);
  }

  @PutMapping("/decks/{deckId}")
  public DeckResponse updateDeck(@PathVariable Long deckId, @Valid @RequestBody DeckRequest request) {
    return adminDeckService.updateDeck(deckId, request);
  }

  @DeleteMapping("/decks/{deckId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteDeck(@PathVariable Long deckId) {
    adminDeckService.deleteDeck(deckId);
  }

  // ---------- Từ trong bộ (thay /admin/decks/{id}/cards) ----------

  @PostMapping("/decks/{deckId}/words")
  @ResponseStatus(HttpStatus.CREATED)
  public WordResponse createWord(@PathVariable Long deckId, @Valid @RequestBody WordRequest request) {
    return adminDeckService.createWord(deckId, request);
  }

  @PostMapping("/decks/{deckId}/words/{wordId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void addWord(@PathVariable Long deckId, @PathVariable Long wordId) {
    adminDeckService.addWord(deckId, wordId);
  }

  @DeleteMapping("/decks/{deckId}/words/{wordId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeWord(@PathVariable Long deckId, @PathVariable Long wordId) {
    adminDeckService.removeWord(deckId, wordId);
  }

  // ---------- Từ (thay /admin/cards/{id}) ----------

  @PutMapping("/words/{wordId}")
  public WordResponse updateWord(@PathVariable Long wordId, @Valid @RequestBody WordRequest request) {
    return adminDeckService.updateWord(wordId, request);
  }

  @DeleteMapping("/words/{wordId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteWord(@PathVariable Long wordId) {
    adminDeckService.deleteWord(wordId);
  }
}
