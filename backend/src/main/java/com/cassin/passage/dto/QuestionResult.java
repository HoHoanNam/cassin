package com.cassin.passage.dto;

import java.util.List;

import com.cassin.passage.QuestionType;
import com.cassin.passage.RejectedReason;
import com.cassin.passage.TfngAnswer;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Kết quả một câu sau khi nộp: câu trả lời của học viên, đáp án đúng, bằng chứng, giải thích, mọi gợi ý.
 * Field của dạng khác để null và bị bỏ khỏi JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionResult(
    Long questionId,
    int position,
    QuestionType type,
    String prompt,
    String instruction,
    // MCQ
    List<OptionResult> options,
    Long selectedOptionId,
    // TFNG
    TfngAnswer yourTfngAnswer,
    TfngAnswer correctTfngAnswer,
    // BLANK
    String yourText,
    List<String> acceptedAnswers,
    RejectedReason rejectedReason,
    // chung
    boolean correct,
    int hintsUsed,
    List<HintView> hints,
    String evidence,
    String explanation) {
}
