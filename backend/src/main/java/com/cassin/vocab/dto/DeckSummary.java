package com.cassin.vocab.dto;

import java.time.Instant;

import com.cassin.vocab.DeckType;

// Một dòng kết quả của câu truy vấn danh sách bộ: có sẵn số thẻ, không phải tải thẻ lên.
public record DeckSummary(
  Long id,
  String deckName,
  String description,
  DeckType deckType,
  Long ownerId,
  Long cardCount,
  // Số thẻ đến hạn và thẻ mới cho từng bộ
  Long dueCount, 
  Long newCount, 
  Instant updatedAt
) {}
