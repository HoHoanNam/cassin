package com.cassin.vocab.dto;

import java.time.Instant;
import java.util.Objects;

import com.cassin.vocab.Deck;
import com.cassin.vocab.DeckType;

/**
 * [V10] cardCount → wordCount (bộ chứa TỪ). dueCount / newCount đếm theo NGHĨA (đơn vị được ôn).
 *
 * @param editable true = bộ của chính user (hiện nút Sửa / Xóa / Thêm từ)
 */
public record DeckResponse(
    Long id,
    String deckName,
    String description,
    DeckType deckType,
    long wordCount,
    long dueCount,
    long newCount,
    boolean editable,
    Instant updatedAt
) {
  public static DeckResponse from(Deck deck, long wordCount, long dueCount, long newCount, Long userId) {
    return new DeckResponse(deck.getId(), deck.getDeckName(), deck.getDescription(), deck.getDeckType(),
        wordCount, dueCount, newCount, deck.isOwnedBy(userId), deck.getUpdatedAt());
  }

  public static DeckResponse from(DeckSummary s, Long userId) {
    return new DeckResponse(s.id(), s.deckName(), s.description(), s.deckType(),
        orZero(s.wordCount()), orZero(s.dueCount()), orZero(s.newCount()),
        Objects.equals(s.ownerId(), userId), s.updatedAt());
  }

  // sum(...) trên tập rỗng có thể trả null tùy CSDL; đổi về 0 cho chắc.
  private static long orZero(Long value) {
    return value == null ? 0 : value;
  }
}
