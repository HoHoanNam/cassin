package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.Deck;
import com.cassin.vocab.Word;

/**
 * Chi tiết một từ: mọi nghĩa (kèm câu ví dụ) + các bộ đang chứa từ.
 *
 * @param editable true = từ của chính user (hiện nút Sửa / Xóa). Từ hệ thống luôn false.
 * @param system   true = từ hệ thống
 * @param decks    các bộ user XEM được đang chứa từ này
 */
public record WordResponse(
    Long id,
    String term,
    String phonetic,
    boolean editable,
    boolean system,
    List<SenseResponse> senses,
    List<DeckRef> decks
) {
  /** Tham chiếu ngắn tới một bộ (đủ để hiện nhãn và link). */
  public record DeckRef(Long id, String deckName) {
    public static DeckRef from(Deck deck) {
      return new DeckRef(deck.getId(), deck.getDeckName());
    }
  }

  public static WordResponse from(Word word, List<Deck> decks, Long userId) {
    return new WordResponse(
        word.getId(),
        word.getTerm(),
        word.getPhonetic(),
        word.isOwnedBy(userId),
        word.isSystem(),
        word.getSenses().stream().map(SenseResponse::from).toList(),
        decks.stream().map(DeckRef::from).toList());
  }
}
