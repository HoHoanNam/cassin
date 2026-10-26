package com.cassin.vocab.dto;

import com.cassin.vocab.DeckType;

// Một dòng kết quả của câu truy vấn danh sách bộ: có sẵn số thẻ, không phải tải thẻ lên.
public record DeckSummary(
  Long id,
  String deckName,
  String description,
  DeckType deckType,
  Long ownerId,
  Long cardCount
) {}
