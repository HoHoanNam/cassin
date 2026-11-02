package com.cassin.practice.dto;

import java.util.List;

/**
 * Một câu hỏi xếp câu: các mảnh của câu ví dụ đã bị xáo trộn.
 *
 * @param term       từ đang học (hiện làm gợi ý: câu phải chứa từ này)
 * @param definition nghĩa đang học
 * @param tokens     các mảnh đã xáo trộn, dấu câu dính theo chữ: ["a", "lot.", "Her", "has", ...]
 */
public record SentenceBuildQuestion(
    Long senseId,
    int exampleIndex,
    String term,
    String definition,
    List<String> tokens
) {
}
