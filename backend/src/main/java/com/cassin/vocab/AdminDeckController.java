package com.cassin.vocab;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.cassin.vocab.dto.CardRequest;
import com.cassin.vocab.dto.CardResponse;
import com.cassin.vocab.dto.DeckRequest;
import com.cassin.vocab.dto.DeckResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDeckController {
  final private AdminDeckService adminDeckService;

  @PostMapping("/decks")
  @ResponseStatus(HttpStatus.CREATED)
  public DeckResponse createDeck(@Valid @RequestBody DeckRequest request) {
    return adminDeckService.createDeck(request);
  }

  @PutMapping("/decks/{deckId}")
  public DeckResponse updateDeck(
      @PathVariable Long deckId,
      @Valid @RequestBody DeckRequest request) {
    return adminDeckService.updateDeck(deckId, request);
  }

  @DeleteMapping("/decks/{deckId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteDeck(@PathVariable Long deckId) {
    adminDeckService.deleteDeck(deckId);
  }

  @PostMapping("/decks/{deckId}/cards")
  @ResponseStatus(HttpStatus.CREATED)
  public CardResponse addCard(
      @PathVariable Long deckId,
      @Valid @RequestBody CardRequest request) {
    return adminDeckService.addCard(deckId, request);
  }

  @PutMapping("/cards/{cardId}")
  public CardResponse updateCard(
      @PathVariable Long cardId,
      @Valid @RequestBody CardRequest request) {
    return adminDeckService.updateCard(cardId, request);
  }

  @DeleteMapping("/cards/{cardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteCard(@PathVariable Long cardId) {
    adminDeckService.deleteCard(cardId);
  }
}
