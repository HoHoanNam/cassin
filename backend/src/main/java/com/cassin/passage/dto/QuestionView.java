package com.cassin.passage.dto;

import java.util.List;

import com.cassin.passage.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Câu hỏi khi ĐANG làm bài. Cố ý KHÔNG có đáp án, bằng chứng, giải thích, gợi ý chưa mở:
 * mọi thứ gửi xuống trình duyệt đều đọc được trong DevTools.
 *
 * @param instruction chỉ BLANK: "NO MORE THAN TWO WORDS"
 * @param options     chỉ MCQ
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionView(
    Long id,
    int position,
    QuestionType type,
    String prompt,
    String instruction,
    List<OptionView> options,
    List<HintView> revealedHints,
    int hintsRemaining) {
}
