package com.cassin.passage.dto;

import java.util.List;

import com.cassin.passage.AnswerKind;
import com.cassin.passage.QuestionType;
import com.cassin.passage.TfngAnswer;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Câu hỏi đầy đủ cho trang soạn bài. Hình dạng gần với QuestionRequest để FE sửa rồi gửi lại. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminQuestionResponse(
    Long id,
    int position,
    QuestionType type,
    String prompt,
    String evidence,
    String explanation,
    List<HintView> hints,
    List<OptionResult> options,
    TfngAnswer answer,
    AnswerKind answerKind,
    Integer maxWords,
    List<String> acceptedAnswers) {
}
