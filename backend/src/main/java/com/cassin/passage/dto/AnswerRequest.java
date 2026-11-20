package com.cassin.passage.dto;

import com.cassin.passage.AnswerInput;
import com.cassin.passage.TfngAnswer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Câu trả lời cho một câu. Chỉ điền field đúng dạng: MCQ → optionId · TFNG → tfngAnswer · BLANK → text. */
public record AnswerRequest(
    @NotNull Long questionId,
    Long optionId,
    TfngAnswer tfngAnswer,
    @Size(max = 100, message = "Câu trả lời tối đa 100 ký tự") String text) {

  public AnswerInput toInput() {
    return new AnswerInput(optionId, tfngAnswer, text);
  }
}
