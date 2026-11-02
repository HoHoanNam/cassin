package com.cassin.vocab.dto;

import java.time.Instant;

import com.cassin.vocab.DeckType;

/**
 * Một dòng kết quả của DeckRepository.findVisibleSummaries ("select new ...DeckSummary(...)").
 * Thứ tự và kiểu tham số phải khớp ĐÚNG thứ tự trong câu JPQL.
 * count(...) và sum(...) của JPQL trả về Long.
 */
public record DeckSummary(
    Long id,
    String deckName,
    String description,
    DeckType deckType,
    Long ownerId,
    Long wordCount,
    Long dueCount,
    Long newCount,
    Instant updatedAt
) {
}
