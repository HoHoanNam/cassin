package com.cassin.vocab;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.vocab.dto.WordRequest;
import com.cassin.vocab.dto.WordResponse;
import com.cassin.vocab.dto.WordSummary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Sổ từ của học viên. Thay cho CardController cũ. */
@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {
  private final WordService wordService;

  /** GET /api/words?q=&deckId=&page=&size=  (deckId bỏ trống = toàn bộ sổ từ) */
  @GetMapping
  public PageResponse<WordSummary> list(
      @RequestParam(required = false) Long deckId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthUser user) {
    return wordService.listWords(deckId, q, page, size, user.id());
  }

  @GetMapping("/{wordId}")
  public WordResponse get(@PathVariable Long wordId, @AuthenticationPrincipal AuthUser user) {
    return wordService.getWord(wordId, user.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WordResponse create(@Valid @RequestBody WordRequest request, @AuthenticationPrincipal AuthUser user) {
    return wordService.createWord(request, user.id());
  }

  @PutMapping("/{wordId}")
  public WordResponse update(
      @PathVariable Long wordId,
      @Valid @RequestBody WordRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return wordService.updateWord(wordId, request, user.id());
  }

  @DeleteMapping("/{wordId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long wordId, @AuthenticationPrincipal AuthUser user) {
    wordService.deleteWord(wordId, user.id());
  }
}
