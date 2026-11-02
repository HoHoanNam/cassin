package com.cassin.practice.dto;

import com.cassin.vocab.PartOfSpeech;

/**
 * Một câu hỏi điền từ. KHÔNG chứa đáp án: client chỉ biết câu đã che + gợi ý.
 * Khi nộp, client gửi lại senseId + exampleIndex để server tự đọc câu gốc và chấm.
 *
 * @param sentence     câu đã che: "Her English has ____ a lot."
 * @param firstLetter  gợi ý chữ cái đầu: "i"
 * @param answerLength độ dài đáp án (số ô gạch dưới ở giao diện)
 */
public record FillBlankQuestion(
    Long senseId,
    int exampleIndex,
    String sentence,
    PartOfSpeech partOfSpeech,
    String definition,
    String firstLetter,
    int answerLength
) {
}
