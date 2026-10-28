package com.cassin.quiz.dto;

import java.util.List;

import com.cassin.quiz.QuizAttempt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitQuizRequest(
  // @Valid trên phần tử: chạy luôn các annotation bên trong AnswerInput cho từng câu.
  @NotNull(message = "Thiếu danh sách câu trả lời")
  @Size(max = QuizAttempt.MAX_QUESTIONS, message = "Tối đa 10 câu trả lời")
  List<@Valid AnswerInput> answers) {

  // optionId được phép null = bỏ trống câu này.
  public record AnswerInput(
    @NotNull(message = "Thiếu id câu hỏi") 
    Long questionId,
    Long optionId
  ) {}
}
