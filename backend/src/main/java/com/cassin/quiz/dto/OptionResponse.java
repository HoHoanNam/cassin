package com.cassin.quiz.dto;

import com.cassin.grammar.QuestionOption;

/**
 * Đáp án DÙNG LÚC LÀM BÀI: chỉ id + nội dung. KHÔNG BAO GIỜ thêm field "correct" vào đây.
 */
public record OptionResponse(Long id, String content) {
  public static OptionResponse from(QuestionOption option) {
    return new OptionResponse(option.getId(), option.getContent());
  }
}

