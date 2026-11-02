package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.PartOfSpeech;
import com.cassin.vocab.Word;

/**
 * Một dòng trong danh sách từ (sổ từ, từ trong bộ): nhẹ hơn WordResponse, không có câu ví dụ
 * và danh sách bộ (không cần cho danh sách, lại tốn thêm truy vấn).
 */
public record WordSummary(Long id, String term, String phonetic, boolean editable, boolean system, List<SenseBrief> senses) {

  public record SenseBrief(Long id, PartOfSpeech partOfSpeech, String definition) {
  }

  public static WordSummary from(Word word, Long userId) {
    return new WordSummary(
        word.getId(),
        word.getTerm(),
        word.getPhonetic(),
        word.isOwnedBy(userId),
        word.isSystem(),
        word.getSenses().stream()
            .map(s -> new SenseBrief(s.getId(), s.getPartOfSpeech(), s.getDefinition()))
            .toList());
  }
}
