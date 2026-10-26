package com.cassin.vocab;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.cassin.auth.AuthUser;
import com.cassin.common.dto.PageResponse;
import com.cassin.vocab.dto.CardRequest;
import com.cassin.vocab.dto.CardResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CardController {
  private final CardService cardService;

  @GetMapping("/decks/{deckId}/cards")
  public PageResponse<CardResponse> list(@PathVariable Long deckId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthUser user) {
    return cardService.listCards(deckId, page, size, user.id());
  }

  @PostMapping("/decks/{deckId}/cards")
  @ResponseStatus(HttpStatus.CREATED)
  public CardResponse create(
      @PathVariable Long deckId,
      @Valid @RequestBody CardRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return cardService.createCard(deckId, request, user.id());
  }

  @PutMapping("/cards/{cardId}")
  public CardResponse update(
      @PathVariable Long cardId,
      @Valid @RequestBody CardRequest request,
      @AuthenticationPrincipal AuthUser user) {
    return cardService.updateCard(cardId, request, user.id());
  }

  @DeleteMapping("/cards/{cardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable Long cardId,
      @AuthenticationPrincipal AuthUser user) {
    cardService.deleteCard(cardId, user.id());
  }
}
