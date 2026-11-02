package com.cassin.vocab.dto;

import java.util.List;

import com.cassin.vocab.PartOfSpeech;
import com.cassin.vocab.Sense;

/**
 * Một nghĩa trong WordResponse.
 * examples trả NGUYÊN VĂN kể cả dấu [ ] để form sửa từ hiện đúng chỗ đã đánh dấu.
 * (Màn ôn thẻ dùng ReviewCardResponse, nơi câu ví dụ đã được bỏ dấu ngoặc.)
 */
public record SenseResponse(Long id, int position, PartOfSpeech partOfSpeech, String definition, List<String> examples) {
  public static SenseResponse from(Sense sense) {
    return new SenseResponse(sense.getId(), sense.getPosition(), sense.getPartOfSpeech(),
        sense.getDefinition(), List.copyOf(sense.getExamples()));
  }
}
