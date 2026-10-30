package com.cassin.grammar.dto;

import java.util.List;

import com.cassin.grammar.Question;

public record AdminQuestionResponse(Long questionId, String content, String explanation, List<Option> options) {
  // Option
  public record Option(Long optionId, String content, boolean correct, int position) {
  }

  // Mapper
  public static AdminQuestionResponse from(Question q) {
    return new AdminQuestionResponse(q.getId(), q.getContent(), q.getExplanation(),
        q.getOptions().stream()
            .map(o -> new Option(o.getId(), o.getContent(), o.isCorrect(), o.getPosition()))
            .toList());
  }
}
